package com.connectedneighbours.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Mini serveur HTTP loopback (JDK standard) qui écoute
 * {@code http://127.0.0.1:<port>/callback?code=...&state=...} pour récupérer
 * le code d'autorisation renvoyé par l'auth-service via le navigateur.
 *
 * <p>Le port est alloué par l'OS (port 0) : une application native ne peut pas
 * réserver un port fixe, d'où l'exigence RFC 8252 §7.3 côté serveur d'accepter
 * n'importe quel port sur la boucle locale.</p>
 *
 * <p>Ce qui transite ici est un <em>code</em> à usage unique, inutilisable sans
 * le verifier PKCE gardé en mémoire par le processus — et non plus l'access
 * token lui-même, qui atterrissait dans l'historique du navigateur.</p>
 */
public class CallbackServer {

    private static final String CALLBACK_PATH = "/callback";

    private static final String PAGE_CSS =
            "<style>body{font-family:system-ui,sans-serif;display:flex;align-items:center;" +
            "justify-content:center;height:100vh;margin:0;background:#f0f2f5;}" +
            ".c{text-align:center;background:#fff;padding:2rem 3rem;border-radius:8px;" +
            "box-shadow:0 2px 8px rgba(0,0,0,.1);}h1{margin:0 0 .5rem}" +
            "p{color:#666;margin:0}.ok{color:#27ae60}.ko{color:#c0392b}</style>";

    private static final String OK_HTML =
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>Connexion</title>" +
            PAGE_CSS + "</head>" +
            "<body><div class=\"c\"><h1 class=\"ok\">✓ Connexion réussie</h1>" +
            "<p>Vous pouvez fermer cet onglet et revenir à l'application.</p></div>" +
            "<script>setTimeout(()=>window.close(),1500);</script></body></html>";

    private static final String ERROR_HTML =
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>Connexion refusée</title>" +
            PAGE_CSS + "</head>" +
            "<body><div class=\"c\"><h1 class=\"ko\">Connexion refusée</h1>" +
            "<p>%s</p></div></body></html>";

    private final HttpServer server;
    private final int port;
    private final String expectedState;
    private final CompletableFuture<String> codeFuture = new CompletableFuture<>();

    /**
     * @param expectedState valeur {@code state} envoyée au /authorize ; le callback
     *                      est rejeté si elle ne revient pas à l'identique
     */
    public CallbackServer(String expectedState) throws IOException {
        if (expectedState == null || expectedState.isBlank()) {
            throw new IllegalArgumentException("state requis");
        }
        this.expectedState = expectedState;
        // Port 0 = allocation dynamique par l'OS.
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.port = server.getAddress().getPort();
        server.createContext(CALLBACK_PATH, new CallbackHandler());
        server.setExecutor(null);
    }

    /** Démarre le serveur. */
    public void start() {
        server.start();
    }

    /** Arrête le serveur. */
    public void stop() {
        server.stop(0);
    }

    /** Port effectif (à utiliser pour construire le redirect_uri). */
    public int getPort() {
        return port;
    }

    /** URL de callback complète (redirect_uri à passer au /authorize). */
    public String getCallbackUrl() {
        return "http://127.0.0.1:" + port + CALLBACK_PATH;
    }

    /**
     * Bloque jusqu'à recevoir le code d'autorisation du navigateur.
     *
     * @param timeoutMillis durée max d'attente
     * @return le code à usage unique
     * @throws TimeoutException     si le navigateur ne répond pas à temps
     * @throws InterruptedException si le thread est interrompu
     * @throws IOException          si le callback signale une erreur ou est invalide
     */
    public String waitForCode(long timeoutMillis)
            throws TimeoutException, InterruptedException, IOException {
        try {
            return codeFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("Callback failed", e);
        }
    }

    static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            try {
                String key = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.putIfAbsent(key, value);
            } catch (Exception ignored) {
                // Paire malformée : on l'ignore plutôt que de rejeter tout le callback.
            }
        }
        return params;
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());

            String state = params.get("state");
            String error = params.get("error");
            String code = params.get("code");

            IOException failure = null;
            // Le state se vérifie en premier : sans lui, n'importe quelle page ouverte
            // sur cette machine pourrait appeler ce callback pendant la fenêtre de login
            // et nous faire consommer un code qu'elle a choisi.
            if (state == null || !expectedState.equals(state)) {
                failure = new IOException("Callback avec un state inattendu — tentative rejetée");
            } else if (error != null) {
                failure = new IOException(describe(error));
            } else if (code == null || code.isBlank()) {
                failure = new IOException("Callback sans code d'autorisation");
            }

            // Répond d'abord au navigateur, puis débloque l'attente.
            String html = failure == null
                    ? OK_HTML
                    : String.format(ERROR_HTML, escape(failure.getMessage()));
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(failure == null ? 200 : 400, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }

            if (failure == null) {
                codeFuture.complete(code);
            } else {
                codeFuture.completeExceptionally(failure);
            }
        }
    }

    /** Traduit les codes d'erreur OAuth que l'auth-service peut renvoyer. */
    private static String describe(String error) {
        return switch (error) {
            case "access_denied" -> "Ce compte n'est pas administrateur.";
            case "invalid_request" -> "Requête d'autorisation invalide.";
            case "unsupported_response_type" -> "Type de réponse non supporté par le serveur.";
            default -> "Connexion refusée par le serveur (" + error + ").";
        };
    }

    private static String escape(String raw) {
        if (raw == null) return "";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
