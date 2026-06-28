package com.connectedneighbours.model;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public class Incident {

    /**
     * Tri par date de création (les dates nulles sont placées à la fin).
     */
    public static final Comparator<Incident> BY_CREATED_AT_ASC =
            Comparator.comparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Tri par date de création décroissante (les plus récents d'abord).
     */
    public static final Comparator<Incident> BY_CREATED_AT_DESC = BY_CREATED_AT_ASC.reversed();

    private String id;
    private String reporterId;
    private String districtId;
    private String category;
    private String description;
    private String photoUrl;
    private String status;
    private String assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<IncidentHistory> history;

    private boolean synced;

    public Incident() {
    }

    public Incident(String id, String reporterId, String districtId, String category, String description) {
        this.id = id;
        this.reporterId = reporterId;
        this.districtId = districtId;
        this.category = category;
        this.description = description;
        this.status = Status.OPEN.getValue();
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

    public List<IncidentHistory> getHistory() {
        return history;
    }

    public void setHistory(List<IncidentHistory> history) {
        this.history = history;
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
                ", history=" + history +
                ", synced=" + synced +
                '}';
    }

    public enum Status {
        OPEN("open", "Ouvert"),
        IN_PROGRESS("in_progress", "En cours"),
        RESOLVED("resolved", "Résolu"),
        CLOSED("closed", "Fermé"),
        UNSYNCED("unsynced", "Non Syncronisé");

        private final String value;
        private final String label;

        Status(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }
    }
}