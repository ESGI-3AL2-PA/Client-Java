package com.connectedneighbours.config;

import java.util.prefs.Preferences;

public class ApiConfig {

    public static final String DEFAULT_SCHEME = BuildConfig.apiScheme();
    public static final String DEFAULT_HOST = BuildConfig.apiHost();
    /** -1 => URL sans suffixe ":port" (cas HTTPS derrière Caddy). */
    public static final int DEFAULT_PORT = BuildConfig.apiPort();
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ApiConfig.class).node(BuildConfig.profile());
    private static final String KEY_SCHEME = "api.scheme";
    private static final String KEY_HOST = "api.host";
    private static final String KEY_PORT = "api.port";

    private ApiConfig() {
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
        // Autorise la saisie d'une IPv6 entre crochets.
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        PREFS.put(KEY_HOST, normalized);
    }

    public static int getPort() {
        // On lit la valeur brute pour pouvoir gérer un port vide (=> pas de port dans l'URL).
        String raw = PREFS.get(KEY_PORT, null);
        if (raw == null) {
            // Non configuré : on conserve le comportement historique (port par défaut).
            return DEFAULT_PORT;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            // Configuré volontairement vide : on ignore le port.
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
            PREFS.put(KEY_PORT, defaultPortText());
            return;
        }
        PREFS.put(KEY_PORT, String.valueOf(port));
    }

    /**
     * Permet de stocker un port optionnel.
     * <ul>
     *   <li>"" (ou null/blank) => port ignoré (URL sans :port)</li>
     *   <li>sinon => doit être un entier 1-65535</li>
     * </ul>
     */
    public static void setPort(String portText) {
        if (portText == null) {
            PREFS.put(KEY_PORT, "");
            return;
        }
        String trimmed = portText.trim();
        if (trimmed.isEmpty()) {
            PREFS.put(KEY_PORT, "");
            return;
        }
        try {
            int port = Integer.parseInt(trimmed);
            setPort(port);
        } catch (NumberFormatException e) {
            PREFS.put(KEY_PORT, defaultPortText());
        }
    }

    /**
     * Valeur du port telle qu'affichée dans l'UI.
     * <ul>
     *   <li>si jamais configuré: retourne le port par défaut</li>
     *   <li>si configuré vide: retourne ""</li>
     * </ul>
     */
    public static String getPortText() {
        String raw = PREFS.get(KEY_PORT, null);
        if (raw == null) {
            return defaultPortText();
        }
        return raw.trim();
    }

    /** Le port par défaut tel qu'affiché/stocké : vide quand le build n'en impose pas. */
    private static String defaultPortText() {
        return DEFAULT_PORT > 0 ? String.valueOf(DEFAULT_PORT) : "";
    }

    /**
     * Port à utiliser pour un test de connectivité TCP.
     * Si le port est vide, on retombe sur 80/443 selon le schéma.
     */
    public static int getPortForSocket() {
        int port = getPort();
        if (port > 0) {
            return port;
        }
        return "https".equals(getScheme()) ? 443 : 80;
    }

    public static String getBaseUrl() {
        String host = getHost();
        // IPv6 : il faut des crochets dans une URL (ex: http://[::1]:3000)
        String hostForUrl = host.contains(":") && !(host.startsWith("[") && host.endsWith("]"))
                ? "[" + host + "]"
                : host;
        int port = getPort();
        if (port <= 0) {
            // Port vide => pas de suffixe ":port".
            return getScheme() + "://" + hostForUrl;
        }
        return getScheme() + "://" + hostForUrl + ":" + port;
    }

    public static void resetToDefaults() {
        setScheme(DEFAULT_SCHEME);
        setHost(DEFAULT_HOST);
        setPort(DEFAULT_PORT);
    }
}
