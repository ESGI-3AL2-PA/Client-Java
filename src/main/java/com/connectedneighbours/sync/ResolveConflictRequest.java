package com.connectedneighbours.sync;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Corps de {@code POST /conflicts/:id/resolve} (§4.3).
 * {@code data} n'est requis (et n'est envoyé) que pour la résolution
 * {@code merged}, où il porte la fusion champ à champ produite par l'UI.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResolveConflictRequest {

    public static final String LOCAL = "local";
    public static final String SERVER = "server";
    public static final String MERGED = "merged";

    private String resolution;
    private Map<String, Object> data;

    public ResolveConflictRequest() {
    }

    public ResolveConflictRequest(String resolution, Map<String, Object> data) {
        this.resolution = resolution;
        this.data = data;
    }

    public static ResolveConflictRequest local() {
        return new ResolveConflictRequest(LOCAL, null);
    }

    public static ResolveConflictRequest server() {
        return new ResolveConflictRequest(SERVER, null);
    }

    public static ResolveConflictRequest merged(Map<String, Object> data) {
        return new ResolveConflictRequest(MERGED, data);
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
