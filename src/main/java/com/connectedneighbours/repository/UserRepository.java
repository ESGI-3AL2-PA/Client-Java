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

    private User extractUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setEmail(rs.getString("email"));
        user.setFirstName(rs.getString("firstName"));
        user.setLastName(rs.getString("lastName"));
        user.setPhone(rs.getString("phone"));
        user.setRole(rs.getString("role"));
        user.setStatus(rs.getString("status"));
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
