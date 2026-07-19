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
 *
 * <p>L'inclusion est réglée champ par champ parce que le schéma de l'api
 * distingue deux choses que Jackson confond : {@code mongoId} et {@code data}
 * sont <em>nullable</em> (la clé doit être présente, la valeur peut être
 * {@code null}), tandis que {@code baseUpdatedAt} est <em>optional</em> (la clé
 * peut manquer, mais un {@code null} explicite est refusé). Le NON_NULL global
 * omettait {@code mongoId} sur un INSERT, ce que l'api rejetait en 400.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngestEvent {

    private long id;
    private String entity;
    private String operation;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String mongoId;

    @JsonInclude(JsonInclude.Include.ALWAYS)
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
