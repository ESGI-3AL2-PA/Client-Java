package com.connectedneighbours.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class SyncLog {

    private Integer id;

    @JsonProperty("table_name")
    private String tableName;

    @JsonProperty("record_id")
    private String recordId;

    private String action;

    private Boolean conflict;

    @JsonProperty("synced_at")
    private LocalDateTime syncedAt;

    public SyncLog() {
    }

    public SyncLog(String tableName, String recordId, String action) {
        this.tableName = tableName;
        this.recordId = recordId;
        this.action = action;
        this.conflict = false;
        this.syncedAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Boolean getConflict() {
        return conflict;
    }

    public void setConflict(Boolean conflict) {
        this.conflict = conflict;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    @Override
    public String toString() {
        return "SyncLog{" +
                "id=" + id +
                ", tableName='" + tableName + '\'' +
                ", recordId='" + recordId + '\'' +
                ", action='" + action + '\'' +
                ", conflict=" + conflict +
                ", syncedAt=" + syncedAt +
                '}';
    }
}

