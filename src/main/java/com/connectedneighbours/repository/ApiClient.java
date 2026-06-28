package com.connectedneighbours.repository;

import com.connectedneighbours.config.ApiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.function.Supplier;

public class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Fournisseur du Bearer token. Par défaut renvoie null (pas d'auth).
     * Rempli par AppContext avec SsoAuthService::getAccessToken.
     * Le setter setToken(String) reste pour les tests unitaires.
     */
    private Supplier<String> tokenSupplier = () -> null;

    public ApiClient() {
    }

    public ApiClient(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier != null ? tokenSupplier : () -> null;
    }

    /**
     * Rétro-compatibilité tests : fixe un token statique.
     */
    public void setToken(String token) {
        this.tokenSupplier = () -> token;
    }

    public void setTokenSupplier(Supplier<String> tokenSupplier) {
        this.tokenSupplier = tokenSupplier != null ? tokenSupplier : () -> null;
    }

    private String authorizationHeader() {
        String token = tokenSupplier.get();
        return token != null ? "Bearer " + token : null;
    }

    private static String buildUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return ApiConfig.getBaseUrl();
        }
        String ep = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return ApiConfig.getBaseUrl() + ep;
    }

    public String get(String endpoint) throws IOException {
        Request.Builder rb = new Request.Builder().url(buildUrl(endpoint));
        String auth = authorizationHeader();
        if (auth != null) rb.addHeader("Authorization", auth);
        Request request = rb.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody responseBody = response.body();
                String responseBodyText = responseBody != null ? responseBody.string() : "corps vide";
                throw new IOException("Erreur HTTP : " + response.code() + " : " + responseBodyText);
            }
            return response.body().string();
        }
    }

    public String post(String endpoint, Object body) throws IOException {
        String json = mapper.writeValueAsString(body);

        Request.Builder rb = new Request.Builder().url(buildUrl(endpoint))
                .post(RequestBody.create(json, JSON));
        String auth = authorizationHeader();
        if (auth != null) rb.addHeader("Authorization", auth);
        Request request = rb.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erreur HTTP: " + response.code());
            return response.body().string();
        }
    }

    public String put(String endpoint, Object body) throws IOException {
        String json = mapper.writeValueAsString(body);

        Request.Builder rb = new Request.Builder().url(buildUrl(endpoint))
                .put(RequestBody.create(json, JSON));
        String auth = authorizationHeader();
        if (auth != null) rb.addHeader("Authorization", auth);
        Request request = rb.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erreur HTTP: " + response.code());
            return response.body().string();
        }
    }

    public String delete(String endpoint) throws IOException {
        Request.Builder rb = new Request.Builder().url(buildUrl(endpoint)).delete();
        String auth = authorizationHeader();
        if (auth != null) rb.addHeader("Authorization", auth);
        Request request = rb.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erreur HTTP: " + response.code());
            return response.body().string();
        }
    }
}
