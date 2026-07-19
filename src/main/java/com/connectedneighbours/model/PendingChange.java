package com.connectedneighbours.model;

import java.time.LocalDateTime;

/**
 * Une écriture locale en attente de push (une ligne de {@code pending_changes}).
 * L'{@code id} sert d'identifiant de corrélation dans le batch envoyé à
 * {@code POST /ingest} : c'est lui que l'api renvoie dans {@code applied[]},
 * {@code conflicts[]} et {@code rejected[]}.
 */
public class PendingChange {

    public static final String INSERT = "INSERT";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";

    private long id;
    private String entity;
    private String recordId;
    private String operation;
    private String mongoId;
    private String payload;
    private String baseUpdatedAt;
    private LocalDateTime occurredAt;

    public PendingChange() {
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

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
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

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getBaseUpdatedAt() {
        return baseUpdatedAt;
    }

    public void setBaseUpdatedAt(String baseUpdatedAt) {
        this.baseUpdatedAt = baseUpdatedAt;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    @Override
    public String toString() {
        return "PendingChange{" +
                "id=" + id +
                ", entity='" + entity + '\'' +
                ", recordId='" + recordId + '\'' +
                ", operation='" + operation + '\'' +
                ", mongoId='" + mongoId + '\'' +
                ", baseUpdatedAt='" + baseUpdatedAt + '\'' +
                ", occurredAt=" + occurredAt +
                '}';
    }
}
