package com.connectedneighbours.repository;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.util.DatabaseUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class IncidentRepository {

    public List<Incident> findAll() {
        String sql = "SELECT * FROM incidents";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public Optional<Incident> findById(String id) {
        String sql = "SELECT * FROM incidents WHERE id = ?";
        try {
            List<Incident> incidents = DatabaseUtil.executeQuery(sql, this::extractIncident, id);
            return incidents.isEmpty() ? Optional.empty() : Optional.of(incidents.get(0));
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public List<Incident> findByStatus(String status) {
        String sql = "SELECT * FROM incidents WHERE status = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident, status);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<Incident> findByReporterId(String reporterId) {
        String sql = "SELECT * FROM incidents WHERE reporterId = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident, reporterId);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<Incident> findByDistrictId(String districtId) {
        String sql = "SELECT * FROM incidents WHERE districtId = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident, districtId);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<Incident> findUnsynced() {
        String sql = "SELECT * FROM incidents WHERE synced = false";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public void save(Incident incident) {
        String sql = "INSERT INTO incidents (id, reporterId, districtId, category, description, photoUrl, status, assignedTo, created_at, updated_at, synced) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            DatabaseUtil.executeUpdate(sql,
                    incident.getId(),
                    incident.getReporterId(),
                    incident.getDistrictId(),
                    incident.getCategory(),
                    incident.getDescription(),
                    incident.getPhotoUrl(),
                    incident.getStatus(),
                    incident.getAssignedTo(),
                    incident.getCreatedAt() != null ? Timestamp.valueOf(incident.getCreatedAt()) : null,
                    incident.getUpdatedAt() != null ? Timestamp.valueOf(incident.getUpdatedAt()) : null,
                    incident.isSynced()
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void update(Incident incident) {
        String sql = "UPDATE incidents SET reporterId = ?, districtId = ?, category = ?, description = ?, photoUrl = ?, status = ?, assignedTo = ?, created_at = ?, updated_at = ?, synced = ? WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    incident.getReporterId(),
                    incident.getDistrictId(),
                    incident.getCategory(),
                    incident.getDescription(),
                    incident.getPhotoUrl(),
                    incident.getStatus(),
                    incident.getAssignedTo(),
                    incident.getCreatedAt() != null ? Timestamp.valueOf(incident.getCreatedAt()) : null,
                    incident.getUpdatedAt() != null ? Timestamp.valueOf(incident.getUpdatedAt()) : null,
                    incident.isSynced(),
                    incident.getId()
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM incidents WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, id);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Incident extractIncident(ResultSet rs) throws SQLException {
        Incident incident = new Incident();
        incident.setId(rs.getString("id"));
        incident.setReporterId(rs.getString("reporterId"));
        incident.setDistrictId(rs.getString("districtId"));
        incident.setCategory(rs.getString("category"));
        incident.setDescription(rs.getString("description"));
        incident.setPhotoUrl(rs.getString("photoUrl"));
        incident.setStatus(rs.getString("status"));
        incident.setAssignedTo(rs.getString("assignedTo"));
        incident.setSynced(rs.getBoolean("synced"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            incident.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            incident.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return incident;
    }
}

