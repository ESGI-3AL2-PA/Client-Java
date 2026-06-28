package com.connectedneighbours.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.connectedneighbours.auth.exception.MfaRequiredException;
import com.connectedneighbours.config.AuthConfig;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service d'authentification SSO contre l'auth-service (port 3001).
 * <p>
 * - access token gardé en mémoire (AtomicReference)
 * - refresh token + csrf_token persistés via PersistentCookieJar (HttpOnly cookie)
 * - getAccessToken() rafraîchit automatiquement si expiré (≤15 min)
 * - logout() révoque côté serveur + purge locale
 */
public class SsoAuthService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final String CSRF_COOKIE_NAME = "csrf_token";

    private final OkHttpClient client;
    private final PersistentCookieJar cookieJar;
    private final ObjectMapper mapper;
    private final JwtVerifier jwtVerifier;

    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final AtomicReference<User> currentUser = new AtomicReference<>();

    public SsoAuthService() {
        this.cookieJar = new PersistentCookieJar();
        this.client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .build();
        this.mapper = JacksonConfig.get();
        this.jwtVerifier = new JwtVerifier();
    }

    public SsoAuthService(OkHttpClient client, PersistentCookieJar cookieJar, JwtVerifier jwtVerifier) {
        this.client = client;
        this.cookieJar = cookieJar;
        this.jwtVerifier = jwtVerifier;
        this.mapper = JacksonConfig.get();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private static Boolean boolOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asBoolean();
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asDouble();
    }

    private static LocalDateTime parseIsoDate(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return null;
        try {
            return Instant.parse(node.asText())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Authentifie l'utilisateur. En cas de succès, stocke l'access token et le user.
     * Si MFA est requis, lève MfaRequiredException (à gérer par l'appelant).
     */
    public User login(String email, String password) throws IOException {
        String body = mapper.writeValueAsString(new LoginBody(email, password));
        Response res = post("/auth/login", body, null);
        try (ResponseBody rb = res.body()) {
            String text = rb != null ? rb.string() : "";

            if (res.code() == 202) {
                JsonNode node = mapper.readTree(text);
                String mfaToken = node.path("mfa_token").asText();
                throw new MfaRequiredException(mfaToken);
            }
            if (!res.isSuccessful()) {
                throw new IOException("Login échec (HTTP " + res.code() + "): " + text);
            }

            JsonNode node = mapper.readTree(text);
            accessToken.set(node.path("access_token").asText());

            User user = parseUser(node.path("user"));
            currentUser.set(user);
            return user;
        }
    }

    /**
     * Complète un login MFA : échange mfa_token + code TOTP contre les tokens réels.
     */
    public User completeMfa(String mfaToken, String code) throws IOException {
        String body = mapper.writeValueAsString(new MfaBody(mfaToken, code));
        Response res = post("/auth/login/mfa", body, null);
        try (ResponseBody rb = res.body()) {
            String text = rb != null ? rb.string() : "";
            if (!res.isSuccessful()) {
                throw new IOException("MFA échec (HTTP " + res.code() + "): " + text);
            }
            JsonNode node = mapper.readTree(text);
            accessToken.set(node.path("access_token").asText());
            User user = parseUser(node.path("user"));
            currentUser.set(user);
            return user;
        }
    }

    /**
     * Tente un refresh silencieux à partir du cookie persistant.
     * Renvoie true si l'access token a pu être renouvelé.
     */
    public boolean tryRefresh() {
        String csrf = cookieJar.findByName(CSRF_COOKIE_NAME);
        if (csrf == null) return false;
        try {
            refresh();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Appelle /auth/refresh et met à jour l'access token + le csrf (cookie).
     */
    public void refresh() throws IOException {
        String csrf = cookieJar.findByName(CSRF_COOKIE_NAME);
        Response res = post("/auth/refresh", "", csrf);
        try (ResponseBody rb = res.body()) {
            String text = rb != null ? rb.string() : "";
            if (!res.isSuccessful()) {
                throw new IOException("Refresh échec (HTTP " + res.code() + "): " + text);
            }
            JsonNode node = mapper.readTree(text);
            accessToken.set(node.path("access_token").asText());
        }
    }

    /**
     * Déconnexion : révoque le refresh token côté serveur, purge les cookies.
     */
    public void logout() {
        String csrf = cookieJar.findByName(CSRF_COOKIE_NAME);
        try {
            post("/auth/logout", "", csrf).close();
        } catch (IOException ignored) {
        }
        accessToken.set(null);
        currentUser.set(null);
        cookieJar.clear();
    }

    /**
     * Renvoie un access token valide, en déclenchant un refresh si expiré.
     * Lève IOException si le refresh échoue (l'appelant doit renvoyer vers login).
     */
    public String getAccessToken() throws IOException {
        String token = accessToken.get();
        if (token != null && !isExpired(token)) {
            return token;
        }
        refresh();
        return accessToken.get();
    }

    /**
     * Indique si une session existe localement (refresh token persistant ou
     * access token encore valide).
     */
    public boolean hasSession() {
        if (accessToken.get() != null && !isExpired(accessToken.get())) {
            return true;
        }
        return cookieJar.findByName(CSRF_COOKIE_NAME) != null;
    }

    /**
     * Indique si l'utilisateur est authentifié avec un access token valide.
     */
    public boolean isAuthenticated() {
        String token = accessToken.get();
        return token != null && !isExpired(token);
    }

    public User getCurrentUser() {
        return currentUser.get();
    }

    public User fetchUserInfo() throws IOException {
        String token = getAccessToken();
        Request req = new Request.Builder()
                .url(url("/auth/userinfo"))
                .addHeader("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response res = client.newCall(req).execute()) {
            ResponseBody rb = res.body();
            String text = rb != null ? rb.string() : "";
            if (!res.isSuccessful()) {
                throw new IOException("userinfo échec (HTTP " + res.code() + "): " + text);
            }
            JsonNode node = mapper.readTree(text);
            User user = parseUser(node);
            currentUser.set(user);
            return user;
        }
    }

    public DecodedJWT verifyToken(String token) {
        return jwtVerifier.verify(token);
    }

    private boolean isExpired(String token) {
        try {
            com.auth0.jwt.interfaces.DecodedJWT jwt = com.auth0.jwt.JWT.decode(token);
            Instant exp = jwt.getExpiresAt().toInstant();
            // Marge de 30s pour éviter de rater la limite côté serveur.
            return exp.isBefore(Instant.now().plusSeconds(30));
        } catch (Exception e) {
            return true;
        }
    }

    private Response post(String endpoint, String body, String csrf) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(url(endpoint))
                .post(RequestBody.create(body, JSON));
        if (csrf != null) {
            rb.addHeader(CSRF_HEADER, csrf);
        }
        return client.newCall(rb.build()).execute();
    }

    private String url(String endpoint) {
        String ep = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return AuthConfig.getBaseUrl() + ep;
    }

    /**
     * Construit un User depuis un JsonNode en gérant le format de date ISO 8601
     * avec suffixe Z (UTC) renvoyé par l'auth-service, que le deserializer par
     * défaut de LocalDateTime (Jackson) ne sait pas parser.
     */
    private User parseUser(JsonNode node) {
        User user = new User();
        user.setId(textOrNull(node, "id"));
        user.setEmail(textOrNull(node, "email"));
        user.setFirstName(textOrNull(node, "firstName"));
        user.setLastName(textOrNull(node, "lastName"));
        user.setPhone(textOrNull(node, "phone"));
        user.setRole(textOrNull(node, "role"));
        user.setAddress(textOrNull(node, "address"));
        user.setDistrictId(textOrNull(node, "districtId"));
        user.setEmailVerified(boolOrNull(node, "emailVerified"));
        user.setTotpEnabled(boolOrNull(node, "totpEnabled"));
        user.setBalance(doubleOrNull(node, "balance"));
        user.setCreatedAt(parseIsoDate(node.path("createdAt")));
        user.setUpdatedAt(parseIsoDate(node.path("updatedAt")));
        return user;
    }

    private record LoginBody(String email, String password) {
    }

    private record MfaBody(String mfa_token, String code) {
    }
}
