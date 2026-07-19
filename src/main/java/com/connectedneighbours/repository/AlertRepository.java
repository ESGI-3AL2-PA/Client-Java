package com.connectedneighbours.repository;

import com.connectedneighbours.model.Alert;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AlertRepository {

    public List<Alert> findRecent(int limit) throws SQLException {
        List<Alert> list = new ArrayList<>();
        String sql = "SELECT * FROM alerts ORDER BY created_at DESC LIMIT ?";
        synchronized (DatabaseManager.class) {
            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
                ps.setInt(1, limit);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Alert a = new Alert();
                    a.setId(rs.getString("id"));
                    a.setMessage(rs.getString("message"));
                    a.setType(rs.getString("type"));
                    a.setRead(rs.getBoolean("read"));
                    a.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    list.add(a);
                }
            }
        }
        return list;
    }
}