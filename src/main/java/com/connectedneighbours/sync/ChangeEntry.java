package com.connectedneighbours.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Une entrée du flux sortant, renvoyée par {@code GET /changes} (§4.2), triée
 * par {@code index} croissant. {@code data} est {@code null} pour un DELETE et
 * expurgé des champs serveur (mot de passe, secret TOTP, {@code _sync}).
 *
 * <p>{@code index} est le curseur : le client le mémorise pour ne redemander
 * que la suite.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangeEntry {

    private long index;
    private String entity;
    private String operation;
    private String mongoId;
    private Map<String, Object> data;
    private String occurredAt;

    public ChangeEntry() {
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getMongoId() {
        return mongoId;
    }

    public void setMongoId(String mongoId) {
        this.mongoId = mongoId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }
}
