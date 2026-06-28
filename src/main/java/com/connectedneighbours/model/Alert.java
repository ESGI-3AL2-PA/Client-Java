package com.connectedneighbours.model;

public class Alert {
    private String id;
    private String type;
    private String message;
    private boolean isRead;
    private java.time.LocalDateTime createdAt;

    public Alert() {}

    public Alert(String type, String message) {
        this.type = type;
        this.message = message;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { this.isRead = read; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
}
