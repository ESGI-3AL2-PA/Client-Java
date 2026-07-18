package com.connectedneighbours.config;

import java.util.Set;
import java.util.prefs.Preferences;

public class AuthConfig {

    public static final String DEFAULT_SCHEME = "http";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 3001;
    public static final String JWT_ISSUER = "auth-service";
    public static final String JWT_AUDIENCE = "api";
    public static final String JWKS_PATH = "/.well-known/jwks.json";

    /** Identifiant public de ce client auprès de l'auth-service (pas un secret). */
    public static final String CLIENT_ID = "admin-desktop";
    public static final String AUTHORIZE_PATH = "/auth/desktop/authorize";
    public static final String TOKEN_PATH = "/auth/desktop/token";

    /**
     * Rôles autorisés à utiliser cette application. L'auth-service refuse déjà
     * tous les autres au moment du /authorize — ce contrôle est une défense en
     * profondeur, pas la barrière : elle est côté serveur, hors de portée d'un
     * patch du jar.
     */
    public static final Set<String> ADMIN_ROLES = Set.of("admin", "superAdmin");
    private static final Preferences PREFS = Preferences.userNodeForPackage(AuthConfig.class);
    private static final String KEY_SCHEME = "auth.scheme";
    private static final String KEY_HOST = "auth.host";
    private static final String KEY_PORT = "auth.port";

    private AuthConfig() {
    }

    public static String getScheme() {
        String scheme = PREFS.get(KEY_SCHEME, DEFAULT_SCHEME);
        if (scheme == null || scheme.isBlank()) {
            return DEFAULT_SCHEME;
        }
        String normalized = scheme.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("https") ? "https" : "http";
    }

    public static void setScheme(String scheme) {
        if (scheme == null || scheme.isBlank()) {
            PREFS.put(KEY_SCHEME, DEFAULT_SCHEME);
            return;
        }
        String normalized = scheme.trim().toLowerCase(java.util.Locale.ROOT);
        PREFS.put(KEY_SCHEME, normalized.equals("https") ? "https" : "http");
    }

    public static String getHost() {
        String host = PREFS.get(KEY_HOST, DEFAULT_HOST);
        if (host == null || host.isBlank()) {
            return DEFAULT_HOST;
        }
        return host;
    }

    public static void setHost(String host) {
        if (host == null || host.isBlank()) {
            PREFS.put(KEY_HOST, DEFAULT_HOST);
            return;
        }
        String normalized = host.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        PREFS.put(KEY_HOST, normalized);
    }

    public static int getPort() {
        String raw = PREFS.get(KEY_PORT, null);
        if (raw == null) {
            return DEFAULT_PORT;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        try {
            int port = Integer.parseInt(trimmed);
            if (port <= 0 || port > 65535) {
                return DEFAULT_PORT;
            }
            return port;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    public static void setPort(int port) {
        if (port <= 0 || port > 65535) {
            PREFS.put(KEY_PORT, String.valueOf(DEFAULT_PORT));
            return;
        }
        PREFS.put(KEY_PORT, String.valueOf(port));
    }

    public static String getBaseUrl() {
        String host = getHost();
        String hostForUrl = host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))
                ? "[" + host + "]"
                : host;
        int port = getPort();
        if (port <= 0) {
            return getScheme() + "://" + hostForUrl;
        }
        return getScheme() + "://" + hostForUrl + ":" + port;
    }

    public static String getJwksUrl() {
        return getBaseUrl() + JWKS_PATH;
    }

    public static String getAuthorizeUrl() {
        return getBaseUrl() + AUTHORIZE_PATH;
    }

    public static String getTokenUrl() {
        return getBaseUrl() + TOKEN_PATH;
    }

    /** True si le rôle porté par le token donne accès à l'application. */
    public static boolean isAdminRole(String role) {
        return role != null && ADMIN_ROLES.contains(role);
    }

    public static void resetToDefaults() {
        setScheme(DEFAULT_SCHEME);
        setHost(DEFAULT_HOST);
        setPort(DEFAULT_PORT);
    }
}
