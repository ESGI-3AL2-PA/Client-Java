package com.connectedneighbours.repository;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.util.DatabaseUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IncidentRepository {

    private static final Logger LOG = Logger.getLogger(IncidentRepository.class.getName());

    public List<Incident> findAll() {
        String sql = "SELECT * FROM INCIDENTS";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<Incident> findAll(int limit) {
        String sql = "SELECT * FROM INCIDENTS LIMIT " + limit;
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<Incident> findByStatus(Incident.Status status) {
        String sql = "SELECT * FROM INCIDENTS WHERE status = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident, status);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }


    public Optional<Incident> findById(String id) {
        String sql = "SELECT * FROM INCIDENTS WHERE id = ?";
        try {
            List<Incident> incidents = DatabaseUtil.executeQuery(sql, this::extractIncident, id);
            return incidents.isEmpty() ? Optional.empty() : Optional.of(incidents.get(0));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return Optional.empty();
        }
    }

    public List<Incident> findByStatus(String status) {
        String sql = "SELECT * FROM INCIDENTS WHERE status = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident, status);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<Incident> findByReporterId(String reporterId) {
        String sql = "SELECT * FROM INCIDENTS WHERE reporterId = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident, reporterId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<Incident> findByDistrictId(String districtId) {
        String sql = "SELECT * FROM INCIDENTS WHERE districtId = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident, districtId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public int countByStatus(String status) {
        try {
            return DatabaseUtil.countRows("INCIDENTS", "status = ?", status);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: countRows INCIDENTS status = ?", e);
            return 0;
        }
    }

    public int countUnsynced() {
        try {
            return DatabaseUtil.getUnsynced("incidents");
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: getUnsynced incidents", e);
            return 0;
        }
    }

    public List<Incident> findUnsynced() {
        String sql = "SELECT * FROM INCIDENTS WHERE synced = false";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractIncident);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public void save(Incident incident) {
        String sql = "INSERT INTO INCIDENTS (id, reporterId, districtId, category, description, photoUrl, status, assignedTo, created_at, updated_at, synced) " +
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
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void update(Incident incident) {
        String sql = "UPDATE INCIDENTS SET reporterId = ?, districtId = ?, category = ?, description = ?, photoUrl = ?, status = ?, assignedTo = ?, created_at = ?, updated_at = ?, synced = ? WHERE id = ?";
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
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM INCIDENTS WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public Optional<Incident> findByMongoId(String mongoId) {
        String sql = "SELECT * FROM INCIDENTS WHERE mongo_id = ?";
        try {
            List<Incident> incidents = DatabaseUtil.executeQuery(sql, this::extractIncident, mongoId);
            return incidents.isEmpty() ? Optional.empty() : Optional.of(incidents.get(0));
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Écrit un incident venu du flux serveur. N'inscrit <em>aucune</em>
     * écriture en attente : ce qui descend du serveur ne doit jamais remonter.
     *
     * <p>MERGE et non INSERT, pour la même raison que
     * {@link UserRepository#saveFromSync} : le pull rejoue des enregistrements
     * déjà connus et l'INSERT violait la clé primaire.</p>
     *
     * @param baseUpdatedAt l'{@code updatedAt} serveur, conservé tel quel comme
     *                      jeton de concurrence optimiste
     */
    public void saveFromSync(Incident incident, String mongoId, String baseUpdatedAt) {
        String sql = "MERGE INTO INCIDENTS (id, reporterId, districtId, category, description, photoUrl, status, assignedTo, created_at, updated_at, synced, mongo_id, base_updated_at) " +
                "KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)";
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
                    mongoId,
                    baseUpdatedAt
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateFromSync(Incident incident, String mongoId, String baseUpdatedAt) {
        String sql = "UPDATE INCIDENTS SET reporterId = ?, districtId = ?, category = ?, description = ?, photoUrl = ?, status = ?, assignedTo = ?, created_at = ?, updated_at = ?, synced = TRUE, base_updated_at = ? WHERE mongo_id = ?";
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
                    baseUpdatedAt,
                    mongoId
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Supprime l'incident correspondant à un DELETE du flux. Silencieux si
     * l'enregistrement n'existe pas localement.
     */
    public void deleteFromSync(String mongoId) {
        String sql = "DELETE FROM INCIDENTS WHERE mongo_id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, mongoId);
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
