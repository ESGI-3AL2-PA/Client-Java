package com.connectedneighbours.model;

import java.time.LocalDateTime;

public class IncidentHistory {

    private String status;
    private String note;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public IncidentHistory() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "IncidentHistory{" +
                "status='" + status + '\'' +
                ", note='" + note + '\'' +
                ", updatedBy='" + updatedBy + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}

