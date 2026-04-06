package com.connectedneighbours.repository;

import com.connectedneighbours.model.User;
import com.connectedneighbours.util.DatabaseUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class UserRepository {

    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractUser);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public Optional<User> findById(String id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            List<User> users = DatabaseUtil.executeQuery(sql, this::extractUser, id);
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public void save(User user) {
        String sql = "INSERT INTO users (id, email, firstName, lastName, phone, role, status, balance, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                    user.getCreatedAt() != null ? Timestamp.valueOf(user.getCreatedAt()) : null,
                    user.getUpdatedAt() != null ? Timestamp.valueOf(user.getUpdatedAt()) : null
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void update(User user) {
        String sql = "UPDATE users SET email = ?, firstName = ?, lastName = ?, phone = ?, role = ?, status = ?, balance = ?, created_at = ?, updated_at = ? WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPhone(),
                    user.getRole(),
                    user.getStatus(),
                    user.getBalance(),
                    user.getCreatedAt() != null ? Timestamp.valueOf(user.getCreatedAt()) : null,
                    user.getUpdatedAt() != null ? Timestamp.valueOf(user.getUpdatedAt()) : null,
                    user.getId()
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, id);
        } catch (SQLException e) {
            e.printStackTrace();
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
