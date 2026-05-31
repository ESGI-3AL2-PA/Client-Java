package com.connectedneighbours.repository;

import com.connectedneighbours.config.ApiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;

public class ApiClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String jwtToken;

    public void setToken(String token) {
        this.jwtToken = token;
    }

    private static String buildUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return ApiConfig.getBaseUrl();
        }
        String ep = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return ApiConfig.getBaseUrl() + ep;
    }

    public String get(String endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(endpoint))
                .addHeader("Authorization", "Bearer " + jwtToken)
                .build();

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

        Request request = new Request.Builder()
                .url(buildUrl(endpoint))
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erreur HTTP: " + response.code());
            return response.body().string();
        }
    }

    public String put(String endpoint, Object body) throws IOException {
        String json = mapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(buildUrl(endpoint))
                .addHeader("Authorization", "Bearer " + jwtToken)
                .put(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erreur HTTP: " + response.code());
            return response.body().string();
        }
    }

    public String delete(String endpoint) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(endpoint))
                .addHeader("Authorization", "Bearer " + jwtToken)
                .delete()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Erreur HTTP: " + response.code());
            return response.body().string();
        }
    }
}
