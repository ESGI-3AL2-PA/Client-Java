package com.connectedneighbours.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Un conflit mis en quarantaine côté serveur (§4.3). {@code localData} est le
 * snapshot que l'opérateur a édité hors-ligne, {@code serverData} l'état
 * serveur (expurgé). Le type vaut {@code update} (concurrence optimiste
 * perdue) ou {@code duplicate} (dédoublonnage sur la clé métier).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Conflict {

    private String id;
    private String entity;
    private String mongoId;
    private String type;
    private String originInstanceId;
    private Map<String, Object> localData;
    private Map<String, Object> serverData;
    private String baseUpdatedAt;
    private String status;
    private String detectedAt;
    private String resolvedAt;
    private String resolvedBy;
    private String resolution;

    public Conflict() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getMongoId() {
        return mongoId;
    }

    public void setMongoId(String mongoId) {
        this.mongoId = mongoId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOriginInstanceId() {
        return originInstanceId;
    }

    public void setOriginInstanceId(String originInstanceId) {
        this.originInstanceId = originInstanceId;
    }

    public Map<String, Object> getLocalData() {
        return localData;
    }

    public void setLocalData(Map<String, Object> localData) {
        this.localData = localData;
    }

    public Map<String, Object> getServerData() {
        return serverData;
    }

    public void setServerData(Map<String, Object> serverData) {
        this.serverData = serverData;
    }

    public String getBaseUpdatedAt() {
        return baseUpdatedAt;
    }

    public void setBaseUpdatedAt(String baseUpdatedAt) {
        this.baseUpdatedAt = baseUpdatedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(String detectedAt) {
        this.detectedAt = detectedAt;
    }

    public String getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(String resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
