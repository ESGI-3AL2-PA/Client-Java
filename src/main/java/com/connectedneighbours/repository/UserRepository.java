package com.connectedneighbours.repository;

import com.connectedneighbours.model.User;
import com.connectedneighbours.util.DatabaseUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserRepository {

    private static final Logger LOG = Logger.getLogger(UserRepository.class.getName());

    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractUser);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            List<User> users = DatabaseUtil.executeQuery(sql, this::extractUser, id);
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return Optional.empty();
        }
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try {
            List<User> users = DatabaseUtil.executeQuery(sql, this::extractUser, email);
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return Optional.empty();
        }
    }

    public List<User> findAdminsByDistrictId(String districtId) {
        String sql = "SELECT * FROM users WHERE role = 'admin' AND districtId = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractUser, districtId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public void save(User user) {
        String sql = "INSERT INTO users (id, email, firstName, lastName, phone, role, status, balance, districtId, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            DatabaseUtil.executeUpdate(sql,
                    user.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPhone(),
                    user.getRole(),
                    user.getStatus(),
                    user.getBalance(),
                    user.getDistrictId(),
                    user.getCreatedAt() != null ? Timestamp.valueOf(user.getCreatedAt()) : null,
                    user.getUpdatedAt() != null ? Timestamp.valueOf(user.getUpdatedAt()) : null
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void update(User user) {
        String sql = "UPDATE users SET email = ?, firstName = ?, lastName = ?, phone = ?, role = ?, status = ?, balance = ?, districtId = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPhone(),
                    user.getRole(),
                    user.getStatus(),
                    user.getBalance(),
                    user.getDistrictId(),
                    user.getCreatedAt() != null ? Timestamp.valueOf(user.getCreatedAt()) : null,
                    user.getUpdatedAt() != null ? Timestamp.valueOf(user.getUpdatedAt()) : null,
                    user.getId()
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public Optional<User> findByMongoId(String mongoId) {
        String sql = "SELECT * FROM users WHERE mongo_id = ?";
        try {
            List<User> users = DatabaseUtil.executeQuery(sql, this::extractUser, mongoId);
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return Optional.empty();
        }
    }

    /**
     * Écrit un utilisateur venu du flux serveur, sans inscrire d'écriture en
     * attente : ce qui descend ne doit jamais remonter.
     *
     * <p>MERGE et non INSERT : un pull rejoue les enregistrements déjà présents
     * localement (curseur relu depuis 0, resynchronisation, redémarrage), et un
     * INSERT y violait la clé primaire — l'exception interrompait alors le reste
     * du lot. Le flux serveur fait autorité, donc réécrire la ligne est correct.</p>
     */
    public void saveFromSync(User user, String mongoId, String baseUpdatedAt) {
        String sql = "MERGE INTO users (id, email, firstName, lastName, phone, role, status, balance, address, districtId, created_at, updated_at, synced, mongo_id, base_updated_at) " +
                "KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)";
        try {
            DatabaseUtil.executeUpdate(sql,
                    user.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPhone(),
                    user.getRole(),
                    user.getStatus(),
                    user.getBalance(),
                    user.getAddress(),
                    user.getDistrictId(),
                    user.getCreatedAt() != null ? Timestamp.valueOf(user.getCreatedAt()) : null,
                    user.getUpdatedAt() != null ? Timestamp.valueOf(user.getUpdatedAt()) : null,
                    mongoId,
                    baseUpdatedAt
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void updateFromSync(User user, String mongoId, String baseUpdatedAt) {
        String sql = "UPDATE users SET email = ?, firstName = ?, lastName = ?, phone = ?, role = ?, status = ?, balance = ?, address = ?, districtId = ?, created_at = ?, updated_at = ?, synced = TRUE, base_updated_at = ? WHERE mongo_id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPhone(),
                    user.getRole(),
                    user.getStatus(),
                    user.getBalance(),
                    user.getAddress(),
                    user.getDistrictId(),
                    user.getCreatedAt() != null ? Timestamp.valueOf(user.getCreatedAt()) : null,
                    user.getUpdatedAt() != null ? Timestamp.valueOf(user.getUpdatedAt()) : null,
                    baseUpdatedAt,
                    mongoId
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void deleteFromSync(String mongoId) {
        String sql = "DELETE FROM users WHERE mongo_id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, mongoId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    private User extractUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setEmail(rs.getString("email"));
        user.setFirstName(rs.getString("firstName"));
        user.setLastName(rs.getString("lastName"));
        user.setPhone(rs.getString("phone"));
        user.setRole(rs.getString("role"));
        user.setStatus(rs.getString("status"));
        user.setAddress(rs.getString("address"));
        user.setDistrictId(rs.getString("districtId"));

        double balance = rs.getDouble("balance");
        if (!rs.wasNull()) {
            user.setBalance(balance);
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            user.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return user;
    }
}
