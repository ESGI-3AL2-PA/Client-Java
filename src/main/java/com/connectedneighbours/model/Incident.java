package com.connectedneighbours.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class Incident {

    private String id;

    @JsonProperty("reporter_id")
    private String reporterId;

    @JsonProperty("district_id")
    private String districtId;
    private String category;
    private String description;

    @JsonProperty("photo_url")
    private String photoUrl;

    private String status;

    @JsonProperty("assigned_to")
    private String assignedTo;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    private boolean synced;

    public Incident() {
    }

    public Incident(String id, String reporterId, String districtId, String category, String description) {
        this.id = id;
        this.reporterId = reporterId;
        this.districtId = districtId;
        this.category = category;
        this.description = description;
        this.status = "OPEN";
        this.synced = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReporterId() {
        return reporterId;
    }

    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }

    public String getDistrictId() {
        return districtId;
    }

    public void setDistrictId(String districtId) {
        this.districtId = districtId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime t) {
        this.createdAt = t;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime t) {
        this.updatedAt = t;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    @Override
    public String toString() {
        return "Incident{" +
                "id='" + id + '\'' +
                ", reporterId='" + reporterId + '\'' +
                ", districtId='" + districtId + '\'' +
                ", category='" + category + '\'' +
                ", description='" + description + '\'' +
                ", photoUrl='" + photoUrl + '\'' +
                ", status='" + status + '\'' +
                ", assignedTo='" + assignedTo + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", synced=" + synced +
                '}';
    }
}