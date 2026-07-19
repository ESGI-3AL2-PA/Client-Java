package com.connectedneighbours.sync;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Un événement local envoyé à {@code POST /ingest} (§4.1).
 *
 * <p>{@code id} est l'identifiant de corrélation stable de l'enregistrement —
 * la clé primaire de sa ligne {@code pending_changes} — que l'api renvoie tel
 * quel dans {@code applied[]} / {@code conflicts[]} / {@code rejected[]}.</p>
 *
 * <p>{@code baseUpdatedAt} n'est envoyé que pour UPDATE/DELETE : c'est le
 * jeton de concurrence optimiste. {@code data} est {@code null} pour un
 * DELETE.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestEvent {

    private long id;
    private String entity;
    private String operation;
    private String mongoId;
    private Map<String, Object> data;
    private String occurredAt;
    private String baseUpdatedAt;

    public IngestEvent() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public String getBaseUpdatedAt() {
        return baseUpdatedAt;
    }

    public void setBaseUpdatedAt(String baseUpdatedAt) {
        this.baseUpdatedAt = baseUpdatedAt;
    }
}
