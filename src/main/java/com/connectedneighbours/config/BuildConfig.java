package com.connectedneighbours.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Valeurs injectées au build par le filtrage de ressources Maven
 * ({@code build.properties} + profil {@code prod}).
 *
 * <p>Ce sont uniquement les <em>défauts</em> : ils alimentent les constantes
 * {@code DEFAULT_*} de {@link ApiConfig} / {@link AuthConfig}, que l'utilisateur
 * peut toujours écraser depuis l'écran Paramètres (stockage {@code java.util.prefs}).
 */
public final class BuildConfig {

    private static final Logger LOGGER = Logger.getLogger(BuildConfig.class.getName());
    private static final String RESOURCE = "/com/connectedneighbours/build.properties";
    private static final Properties PROPS = load();

    private BuildConfig() {
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = BuildConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warning("build.properties absent du classpath — repli sur la stack locale.");
                return props;
            }
            props.load(in);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Lecture de build.properties impossible — repli sur la stack locale.", e);
        }
        return props;
    }

    private static String scheme(String key, String fallback) {
        String value = PROPS.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return "https".equalsIgnoreCase(value.trim()) ? "https" : "http";
    }

    private static String host(String key, String fallback) {
        String value = PROPS.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Un port vide ou invalide vaut -1 : l'URL est alors construite sans suffixe {@code :port}. */
    private static int port(String key, int fallback) {
        String value = PROPS.getProperty(key);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        try {
            int port = Integer.parseInt(trimmed);
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String apiScheme() {
        return scheme("api.scheme", "http");
    }

    public static String apiHost() {
        return host("api.host", "localhost");
    }

    public static int apiPort() {
        return port("api.port", 3000);
    }

    public static String authScheme() {
        return scheme("auth.scheme", "http");
    }

    public static String authHost() {
        return host("auth.host", "localhost");
    }

    public static int authPort() {
        return port("auth.port", 3001);
    }
}
