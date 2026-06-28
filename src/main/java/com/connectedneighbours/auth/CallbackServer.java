package com.connectedneighbours.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Mini serveur HTTP loopback (JDK standard) qui écoute
 * {@code http://127.0.0.1:<port>/callback?access_token=...} pour récupérer
 * l'access token renvoyé par la page de login du navigateur.
 *
 * <p>Pattern : on démarre le serveur sur un port dynamique, on ouvre le
 * navigateur vers la page /login de l'auth-service avec
 * {@code redirect_uri=http://127.0.0.1:<port>/callback}, puis on bloque sur
 * {@link #waitForToken(long)} jusqu'à ce que le navigateur redirige vers
 * ce callback avec le token.</p>
 */
public class CallbackServer {

    private static final String CALLBACK_PATH = "/callback";
    private static final String OK_HTML =
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>Connexion</title>" +
            "<style>body{font-family:system-ui,sans-serif;display:flex;align-items:center;" +
            "justify-content:center;height:100vh;margin:0;background:#f0f2f5;}" +
            ".c{text-align:center;background:#fff;padding:2rem 3rem;border-radius:8px;" +
            "box-shadow:0 2px 8px rgba(0,0,0,.1);}h1{color:#27ae60;margin:0 0 .5rem}" +
            "p{color:#666;margin:0}</style></head>" +
            "<body><div class=\"c\"><h1>✓ Connexion réussie</h1>" +
            "<p>Vous pouvez fermer cet onglet et revenir à l'application.</p></div>" +
            "<script>setTimeout(()=>window.close(),1500);</script></body></html>";

    private final HttpServer server;
    private final int port;
    private final CompletableFuture<String> tokenFuture = new CompletableFuture<>();

    public CallbackServer() throws IOException {
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

    /** URL de callback complète (redirect_uri à passer à la page /login). */
    public String getCallbackUrl() {
        return "http://127.0.0.1:" + port + CALLBACK_PATH;
    }

    /**
     * Bloque jusqu'à recevoir l'access token du navigateur.
     *
     * @param timeoutMillis durée max d'attente
     * @return le token
     * @throws TimeoutException       si le navigateur ne répond pas à temps
     * @throws InterruptedException   si le thread est interrompu
     * @throws IOException            si le callback échoue
     */
    public String waitForToken(long timeoutMillis)
            throws TimeoutException, InterruptedException, IOException {
        try {
            return tokenFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IOException("Callback failed", e);
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String token = extractToken(query);

            // Répond avant tout au navigateur (page "vous pouvez fermer").
            byte[] body = OK_HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }

            // Puis notifie l'attente.
            if (token != null) {
                tokenFuture.complete(token);
            } else {
                tokenFuture.completeExceptionally(
                        new IOException("Callback sans access_token"));
            }
        }

        private String extractToken(String query) {
            if (query == null) return null;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals("access_token")) {
                    try {
                        return java.net.URLDecoder.decode(
                                pair.substring(eq + 1), StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
            return null;
        }
    }
}
