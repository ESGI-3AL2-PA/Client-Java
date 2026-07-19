package com.connectedneighbours.repository;

import com.connectedneighbours.config.ApiConfig;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.config.SyncConfig;
import com.connectedneighbours.sync.ChangeEntry;
import com.connectedneighbours.sync.Conflict;
import com.connectedneighbours.sync.IngestEvent;
import com.connectedneighbours.sync.IngestResult;
import com.connectedneighbours.sync.ResolveConflictRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client HTTP des routes de synchronisation, portées par l'api (:3000) — pas
 * de service dédié. Toutes les requêtes portent le JWT de l'opérateur (le même
 * que {@link ApiClient}, fourni par {@code SsoAuthService}) et l'en-tête
 * {@code X-Sync-Instance} qui identifie cette installation.
 *
 * <p>Le fournisseur de token lève
 * {@link com.connectedneighbours.auth.exception.TokenUnavailableException}
 * quand l'access token en mémoire a expiré ; un refus serveur remonte en
 * {@link ApiException} avec son code. Les deux mènent à
 * {@link com.connectedneighbours.service.SyncStatus#AUTH_REQUIRED}.</p>
 */
public class SyncApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String INSTANCE_HEADER = "X-Sync-Instance";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = JacksonConfig.get();
    private final Supplier<String> tokenSupplier;
    private final SyncConfig syncConfig;

    public SyncApiClient(Supplier<String> tokenSupplier) {
        this(tokenSupplier, new SyncConfig());
    }

    public SyncApiClient(Supplier<String> tokenSupplier, SyncConfig syncConfig) {
        this.tokenSupplier = tokenSupplier != null ? tokenSupplier : () -> null;
        this.syncConfig = syncConfig;
    }

    /**
     * Pousse un lot d'écritures locales (100 max côté serveur).
     */
    public IngestResult ingest(List<IngestEvent> events) throws IOException {
        String body = mapper.writeValueAsString(events);
        Request request = authenticated(new Request.Builder().url(url("/sync/ingest")))
                .post(RequestBody.create(body, JSON))
                .build();
        return mapper.readValue(execute(request), IngestResult.class);
    }

    /**
     * Lit la suite du flux sortant. {@code since = 0} renvoie le snapshot
     * complet — c'est l'unique chemin de pull, il n'y a pas de bootstrap REST.
     * L'exclusion des entrées que cette instance a elle-même produites est
     * déduite serveur-side de {@code X-Sync-Instance}.
     */
    public List<ChangeEntry> changes(long since, int limit) throws IOException {
        Request request = authenticated(
                new Request.Builder().url(url("/sync/changes?since=" + since + "&limit=" + limit)))
                .get()
                .build();
        return readList(execute(request), ChangeEntry[].class);
    }

    /**
     * Les conflits <em>que cette instance a levés</em> et qui restent à
     * résoudre — le seul écran de résolution est le client lourd (§6.5).
     */
    public List<Conflict> myConflicts(int limit) throws IOException {
        Request request = authenticated(
                new Request.Builder().url(url("/sync/conflicts?mine=true&status=pending&limit=" + limit)))
                .get()
                .build();
        return readList(execute(request), Conflict[].class);
    }

    public void resolveConflict(String conflictId, ResolveConflictRequest resolution) throws IOException {
        String body = mapper.writeValueAsString(resolution);
        Request request = authenticated(
                new Request.Builder().url(url("/sync/conflicts/" + conflictId + "/resolve")))
                .post(RequestBody.create(body, JSON))
                .build();
        execute(request);
    }

    /**
     * GET générique, pour les endpoints de lecture (ex. totaux de ressources)
     * qui n'ont pas de méthode dédiée ci-dessus.
     */
    public String get(String endpoint) throws IOException {
        Request request = authenticated(new Request.Builder().url(url(endpoint))).get().build();
        return execute(request);
    }

    private Request.Builder authenticated(Request.Builder builder) {
        String token = tokenSupplier.get();
        if (token != null) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        builder.addHeader(INSTANCE_HEADER, syncConfig.getInstanceId());
        return builder;
    }

    private String execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new ApiException(response.code(),
                        "Erreur HTTP : " + response.code() + " : " + text);
            }
            return text;
        }
    }

    /**
     * Les listes de l'api sont parfois enveloppées dans {@code data} : on
     * déballe comme le reste du client.
     */
    private <T> List<T> readList(String json, Class<T[]> arrayType) throws IOException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root = mapper.readTree(json);
        JsonNode payload = root.has("data") ? root.get("data") : root;
        return List.of(mapper.treeToValue(payload, arrayType));
    }

    private String url(String endpoint) {
        String ep = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return ApiConfig.getBaseUrl() + ep;
    }
}
