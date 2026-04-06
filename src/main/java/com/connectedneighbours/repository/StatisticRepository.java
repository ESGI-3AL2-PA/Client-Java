package com.connectedneighbours.repository;

import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.util.DatabaseUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class StatisticRepository {

    public List<Statistic> findAll() {
        String sql = "SELECT * FROM statistics";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractStatistic);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public Optional<Statistic> findById(Integer id) {
        String sql = "SELECT * FROM statistics WHERE id = ?";
        try {
            List<Statistic> statistics = DatabaseUtil.executeQuery(sql, this::extractStatistic, id);
            return statistics.isEmpty() ? Optional.empty() : Optional.of(statistics.get(0));
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public List<Statistic> findByMetricKey(String metricKey) {
        String sql = "SELECT * FROM statistics WHERE metric_key = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractStatistic, metricKey);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<Statistic> findByPeriod(String period) {
        String sql = "SELECT * FROM statistics WHERE period = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractStatistic, period);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<Statistic> findByMetricKeyAndPeriod(String metricKey, String period) {
        String sql = "SELECT * FROM statistics WHERE metric_key = ? AND period = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractStatistic, metricKey, period);
        } catch (SQLException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public void save(Statistic statistic) {
        String sql = "INSERT INTO statistics (metric_key, metric_value, period, recorded_at) " +
                "VALUES (?, ?, ?, ?)";
        try {
            DatabaseUtil.executeUpdate(sql,
                    statistic.getMetricKey(),
                    statistic.getMetricValue(),
                    statistic.getPeriod(),
                    statistic.getRecordedAt() != null ? Timestamp.valueOf(statistic.getRecordedAt()) : null
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void update(Statistic statistic) {
        String sql = "UPDATE statistics SET metric_key = ?, metric_value = ?, period = ?, recorded_at = ? WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    statistic.getMetricKey(),
                    statistic.getMetricValue(),
                    statistic.getPeriod(),
                    statistic.getRecordedAt() != null ? Timestamp.valueOf(statistic.getRecordedAt()) : null,
                    statistic.getId()
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void delete(Integer id) {
        String sql = "DELETE FROM statistics WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, id);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private Statistic extractStatistic(ResultSet rs) throws SQLException {
        Statistic statistic = new Statistic();
        statistic.setId(rs.getInt("id"));
        statistic.setMetricKey(rs.getString("metric_key"));
        statistic.setMetricValue(rs.getDouble("metric_value"));
        statistic.setPeriod(rs.getString("period"));

        Timestamp recordedAt = rs.getTimestamp("recorded_at");
        if (recordedAt != null) {
            statistic.setRecordedAt(recordedAt.toLocalDateTime());
        }

        return statistic;
    }
}

