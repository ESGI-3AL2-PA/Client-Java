package com.connectedneighbours.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.connectedneighbours.auth.exception.NotAdminException;
import com.connectedneighbours.auth.exception.TokenUnavailableException;
import com.connectedneighbours.config.AuthConfig;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service d'authentification SSO contre l'auth-service (port 3001).
 *
 * <p>Flow desktop : authorization code + PKCE (RFC 8252, application native).
 * Client <em>public</em> — aucun secret client, le jar n'en garderait aucun.</p>
 * <ol>
 *   <li>Démarre un mini serveur HTTP sur 127.0.0.1 (port dynamique).</li>
 *   <li>Ouvre le navigateur système sur {@code /auth/desktop/authorize} avec
 *       {@code state} et {@code code_challenge} (S256).</li>
 *   <li>Le navigateur gère le formulaire (et le MFA le cas échéant). Si un
 *       cookie refresh valide existe (≤7j), l'auth-service reconnaît la
 *       session sans reposer de question → SSO transparent.</li>
 *   <li>Le callback reçoit un {@code code} à usage unique — jamais le token,
 *       qui n'a donc plus à transiter par une URL.</li>
 *   <li>Le code est échangé hors navigateur contre l'access token, lequel est
 *       vérifié (RS256 + issuer + audience) puis contrôlé sur son rôle.</li>
 *   <li>{@link #getAccessToken()} renvoie le token s'il est valide, sinon
 *       lève {@link TokenUnavailableException} pour déclencher un nouveau
 *       login via le navigateur.</li>
 * </ol>
 *
 * <p>Seuls les comptes {@code admin}/{@code superAdmin} obtiennent un code :
 * l'auth-service refuse les autres au /authorize, et le contrôle est refait
 * ici en défense en profondeur.</p>
 *
 * <p>Pas de persistance locale du refresh token : il reste dans le navigateur
 * (cookie HttpOnly). L'app Java ne garde que l'access token en mémoire.</p>
 */
public class SsoAuthService {

    private static final long LOGIN_TIMEOUT_MS = 5 * 60 * 1000; // 5 min

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final JwtVerifier jwtVerifier;

    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final AtomicReference<User> currentUser = new AtomicReference<>();

    public SsoAuthService() {
        this.client = new OkHttpClient();
        this.mapper = JacksonConfig.get();
        this.jwtVerifier = new JwtVerifier();
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
     * Ouvre le navigateur sur la page /login de l'auth-service, attend que
     * l'utilisateur s'y authentifie (ou que le refresh silencieux réussisse),
     * puis récupère l'access token renvoyé au callback loopback.
     *
     * @return l'utilisateur authentifié (fetch depuis /auth/userinfo)
     */
    public User loginViaBrowser() throws IOException {
        return loginViaBrowser(false);
    }

    /**
     * Variante explicite du login navigateur.
     *
     * @param forceReauth {@code true} pour exiger une ré-authentification même si le
     *                    cookie refresh du navigateur est encore valide. Indispensable
     *                    après une déconnexion : sans cela l'authorize rend un code
     *                    pour le même compte dans la milliseconde, et changer
     *                    d'utilisateur devient impossible.
     */
    public User loginViaBrowser(boolean forceReauth) throws IOException {
        PkceChallenge pkce = PkceChallenge.generate();
        String state = PkceChallenge.randomState();

        CallbackServer server = new CallbackServer(state);
        try {
            server.start();

            String redirectUri = server.getCallbackUrl();
            openBrowser(buildAuthorizeUrl(redirectUri, state, pkce.challenge(), forceReauth));

            // Le navigateur ne rapporte qu'un code : le token, lui, ne transite
            // que par l'échange direct ci-dessous.
            String code = server.waitForCode(LOGIN_TIMEOUT_MS);
            String token = exchangeCode(code, redirectUri, pkce.verifier());

            // Vérification réelle (signature RS256 + issuer + audience) avant de
            // faire quoi que ce soit du token : ce qui arrive du réseau n'est pas
            // digne de confiance parce qu'il ressemble à un JWT.
            DecodedJWT jwt = jwtVerifier.verify(token);

            String role = jwt.getClaim("role").asString();
            if (!AuthConfig.isAdminRole(role)) {
                throw new NotAdminException(role);
            }

            accessToken.set(token);
            return fetchUserInfo();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Login interrompu", e);
        } catch (NotAdminException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof IOException io) throw io;
            throw new IOException("Login échec: " + e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    private static String enc(String raw) {
        return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    String buildAuthorizeUrl(String redirectUri, String state, String codeChallenge, boolean forceReauth) {
        return AuthConfig.getAuthorizeUrl()
                + "?response_type=code"
                + "&client_id=" + enc(AuthConfig.CLIENT_ID)
                + "&redirect_uri=" + enc(redirectUri)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256"
                + (forceReauth ? "&prompt=login" : "");
    }

    /**
     * Échange le code contre un access token, hors navigateur. Pas de secret
     * client : c'est le verifier PKCE qui prouve que l'échange vient bien du
     * processus ayant initié le flow.
     */
    private String exchangeCode(String code, String redirectUri, String codeVerifier) throws IOException {
        FormBody form = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", AuthConfig.CLIENT_ID)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", codeVerifier)
                .build();

        Request req = new Request.Builder().url(AuthConfig.getTokenUrl()).post(form).build();
        try (Response res = client.newCall(req).execute()) {
            ResponseBody rb = res.body();
            String text = rb != null ? rb.string() : "";
            if (!res.isSuccessful()) {
                if (res.code() == 403) {
                    throw new NotAdminException(null);
                }
                throw new IOException("Échange du code échoué (HTTP " + res.code() + "): " + text);
            }
            JsonNode node = mapper.readTree(text);
            String token = textOrNull(node, "access_token");
            if (token == null || token.isBlank()) {
                throw new IOException("Réponse du token sans access_token");
            }
            return token;
        }
    }

    /**
     * Déconnexion locale : efface le token et le user en mémoire.
     * Le cookie refresh reste dans le navigateur (≤7j) — l'utilisateur peut
     * le révoquer depuis l'auth-service s'il le souhaite.
     */
    public void logout() {
        accessToken.set(null);
        currentUser.set(null);
    }

    /**
     * Renvoie un access token valide. Lève {@link TokenUnavailableException}
     * si le token est expiré ou absent — l'appelant doit alors relancer
     * {@link #loginViaBrowser()} (le navigateur tentera le refresh silencieux).
     */
    public String getAccessToken() {
        String token = accessToken.get();
        if (token != null && !isExpired(token)) {
            return token;
        }
        throw new TokenUnavailableException(
                new IOException("Access token expiré ou absent — re-login requis"));
    }

    /**
     * Récupère l'utilisateur courant via /auth/userinfo.
     */
    public User fetchUserInfo() throws IOException {
        String token = accessToken.get();
        if (token == null) {
            throw new IOException("Pas d'access token — login d'abord");
        }
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

    public boolean isAuthenticated() {
        String token = accessToken.get();
        return token != null && !isExpired(token);
    }

    public User getCurrentUser() {
        return currentUser.get();
    }

    public DecodedJWT verifyToken(String token) {
        return jwtVerifier.verify(token);
    }

    private boolean isExpired(String token) {
        try {
            DecodedJWT jwt = com.auth0.jwt.JWT.decode(token);
            Instant exp = jwt.getExpiresAt().toInstant();
            // Marge de 30s pour éviter de rater la limite côté serveur.
            return exp.isBefore(Instant.now().plusSeconds(30));
        } catch (Exception e) {
            return true;
        }
    }

    private void openBrowser(String url) throws IOException {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Ouverture du navigateur non supportée sur ce système");
        }
        Desktop.getDesktop().browse(URI.create(url));
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
}
