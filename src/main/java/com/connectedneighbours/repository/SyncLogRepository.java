package com.connectedneighbours.repository;

import com.connectedneighbours.model.SyncLog;
import com.connectedneighbours.util.DatabaseUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SyncLogRepository {

    private static final Logger LOG = Logger.getLogger(SyncLogRepository.class.getName());

    public List<SyncLog> findAll() {
        String sql = "SELECT * FROM sync_log";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractSyncLog);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public Optional<SyncLog> findById(Integer id) {
        String sql = "SELECT * FROM sync_log WHERE id = ?";
        try {
            List<SyncLog> logs = DatabaseUtil.executeQuery(sql, this::extractSyncLog, id);
            return logs.isEmpty() ? Optional.empty() : Optional.of(logs.get(0));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return Optional.empty();
        }
    }

    public List<SyncLog> findByTableName(String tableName) {
        String sql = "SELECT * FROM sync_log WHERE table_name = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractSyncLog, tableName);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<SyncLog> findByRecordId(String recordId) {
        String sql = "SELECT * FROM sync_log WHERE record_id = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractSyncLog, recordId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<SyncLog> findByAction(String action) {
        String sql = "SELECT * FROM sync_log WHERE action = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractSyncLog, action);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<SyncLog> findConflicts() {
        String sql = "SELECT * FROM sync_log WHERE conflict = true";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractSyncLog);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public List<SyncLog> findByTableNameAndRecordId(String tableName, String recordId) {
        String sql = "SELECT * FROM sync_log WHERE table_name = ? AND record_id = ?";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractSyncLog, tableName, recordId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public void save(SyncLog syncLog) {
        String sql = "INSERT INTO sync_log (table_name, record_id, action, conflict, synced_at) " +
                "VALUES (?, ?, ?, ?, ?)";
        try {
            DatabaseUtil.executeUpdate(sql,
                    syncLog.getTableName(),
                    syncLog.getRecordId(),
                    syncLog.getAction(),
                    syncLog.getConflict() != null ? syncLog.getConflict() : false,
                    syncLog.getSyncedAt() != null ? Timestamp.valueOf(syncLog.getSyncedAt()) : null
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void update(SyncLog syncLog) {
        String sql = "UPDATE sync_log SET table_name = ?, record_id = ?, action = ?, conflict = ?, synced_at = ? WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    syncLog.getTableName(),
                    syncLog.getRecordId(),
                    syncLog.getAction(),
                    syncLog.getConflict() != null ? syncLog.getConflict() : false,
                    syncLog.getSyncedAt() != null ? Timestamp.valueOf(syncLog.getSyncedAt()) : null,
                    syncLog.getId()
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void delete(Integer id) {
        String sql = "DELETE FROM sync_log WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void deleteByTableNameAndRecordId(String tableName, String recordId) {
        String sql = "DELETE FROM sync_log WHERE table_name = ? AND record_id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, tableName, recordId);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    private SyncLog extractSyncLog(ResultSet rs) throws SQLException {
        SyncLog syncLog = new SyncLog();
        syncLog.setId(rs.getInt("id"));
        syncLog.setTableName(rs.getString("table_name"));
        syncLog.setRecordId(rs.getString("record_id"));
        syncLog.setAction(rs.getString("action"));
        syncLog.setConflict(rs.getBoolean("conflict"));

        Timestamp syncedAt = rs.getTimestamp("synced_at");
        if (syncedAt != null) {
            syncLog.setSyncedAt(syncedAt.toLocalDateTime());
        }

        return syncLog;
    }
}

