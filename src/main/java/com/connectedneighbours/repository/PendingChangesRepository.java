package com.connectedneighbours.repository;

import com.connectedneighbours.model.PendingChange;
import com.connectedneighbours.sync.SyncEntity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès à {@code pending_changes} : la file des écritures locales à pousser.
 *
 * <p>Une ligne par enregistrement sale — la table <em>est</em> l'état compacté,
 * il n'y a donc pas de passe de compaction séparée. La collapse se fait en
 * ligne dans {@link #upsert} : un INSERT pas encore poussé suivi d'un UPDATE
 * reste un INSERT (avec la nouvelle charge utile), et un INSERT suivi d'un
 * DELETE supprime simplement la ligne — créé-puis-supprimé hors-ligne
 * s'annule, le serveur n'a jamais rien à en savoir.</p>
 *
 * <p>Contrairement aux autres repositories, celui-ci ne passe pas par
 * {@link com.connectedneighbours.util.DatabaseUtil} : il a besoin d'enchaîner
 * plusieurs requêtes sur la <em>même</em> connexion (lecture puis écriture de
 * la collapse) et les tests l'attaquent sur une base H2 en mémoire.</p>
 */
public class PendingChangesRepository {

    private final ConnectionSupplier connections;

    public PendingChangesRepository() {
        this(DatabaseManager::getConnection);
    }

    public PendingChangesRepository(ConnectionSupplier connections) {
        this.connections = connections;
    }

    /**
     * Enregistre (ou fusionne) une écriture locale pour un enregistrement.
     *
     * @param entity        nom protocolaire de l'entité ({@code user}, {@code incident})
     * @param recordId      identifiant local (H2) de l'enregistrement
     * @param operation     INSERT, UPDATE ou DELETE
     * @param mongoId       identifiant serveur, ou {@code null} s'il n'est pas encore assigné
     * @param payload       snapshot JSON des champs poussables, {@code null} pour un DELETE
     * @param baseUpdatedAt jeton de concurrence optimiste (le dernier {@code updatedAt} synchronisé)
     */
    public void upsert(String entity, String recordId, String operation,
                       String mongoId, String payload, String baseUpdatedAt) {
        try {
            Connection conn = connections.get();
            Optional<PendingChange> existing = findByKey(conn, entity, recordId);

            String effectiveOperation = operation;
            if (existing.isPresent()) {
                String pendingOperation = existing.get().getOperation();
                if (PendingChange.INSERT.equals(pendingOperation)) {
                    if (PendingChange.DELETE.equals(operation)) {
                        // Créé puis supprimé hors-ligne : rien à pousser.
                        delete(entity, recordId);
                        return;
                    }
                    // L'enregistrement n'existe toujours pas côté serveur :
                    // l'UPDATE doit rester un INSERT, avec la charge fraîche.
                    effectiveOperation = PendingChange.INSERT;
                }
            }

            String sql = "MERGE INTO pending_changes " +
                    "(entity, record_id, operation, mongo_id, payload, base_updated_at, occurred_at) " +
                    "KEY (entity, record_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entity);
                stmt.setString(2, recordId);
                stmt.setString(3, effectiveOperation);
                stmt.setString(4, mongoId);
                stmt.setString(5, payload);
                stmt.setString(6, baseUpdatedAt);
                stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            log("Impossible d'enregistrer l'écriture locale " + entity + "/" + recordId, e);
        }
    }

    /**
     * Les prochaines écritures à pousser, les plus anciennes d'abord.
     * La table portant déjà une ligne par enregistrement, aucune compaction
     * n'est nécessaire ici.
     */
    public List<PendingChange> findBatch(int limit) {
        String sql = "SELECT * FROM pending_changes ORDER BY occurred_at, id LIMIT ?";
        try {
            Connection conn = connections.get();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<PendingChange> batch = new ArrayList<>();
                    while (rs.next()) {
                        batch.add(extract(rs));
                    }
                    return batch;
                }
            }
        } catch (SQLException e) {
            log("Impossible de lire les écritures en attente", e);
            return List.of();
        }
    }

    public int count() {
        String sql = "SELECT COUNT(*) FROM pending_changes";
        try {
            Connection conn = connections.get();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            log("Impossible de compter les écritures en attente", e);
            return 0;
        }
    }

    public Optional<PendingChange> findByKey(String entity, String recordId) {
        try {
            return findByKey(connections.get(), entity, recordId);
        } catch (SQLException e) {
            log("Impossible de lire l'écriture en attente " + entity + "/" + recordId, e);
            return Optional.empty();
        }
    }

    /**
     * Inscrit l'identifiant serveur fraîchement assigné sur la ligne métier
     * (qui devient donc synchronisée) et sur l'écriture en attente.
     */
    public void setRecordMongoId(String entity, String recordId, String mongoId) {
        SyncEntity target = SyncEntity.fromWire(entity);
        if (target == null) {
            return;
        }
        try {
            Connection conn = connections.get();
            String recordSql = "UPDATE " + target.getTable()
                    + " SET mongo_id = ?, synced = TRUE WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(recordSql)) {
                stmt.setString(1, mongoId);
                stmt.setString(2, recordId);
                stmt.executeUpdate();
            }
            String pendingSql = "UPDATE pending_changes SET mongo_id = ? WHERE entity = ? AND record_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(pendingSql)) {
                stmt.setString(1, mongoId);
                stmt.setString(2, entity);
                stmt.setString(3, recordId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            log("Impossible d'assigner le mongo_id sur " + entity + "/" + recordId, e);
        }
    }

    /**
     * Acquittement d'un événement appliqué : avance le jeton de concurrence
     * optimiste depuis l'ack, puis retire l'écriture en attente.
     *
     * <p>La garde sur {@code occurred_at} est essentielle : si l'utilisateur a
     * re-modifié l'enregistrement pendant le push, la ligne a été re-datée et
     * survit à la purge — sa modification n'est pas perdue.</p>
     *
     * @param sentOccurredAt l'horodatage de la ligne telle qu'elle a été envoyée
     */
    public void advanceBaseAndClear(String entity, String recordId, String mongoId,
                                    String updatedAt, LocalDateTime sentOccurredAt) {
        SyncEntity target = SyncEntity.fromWire(entity);
        if (target == null) {
            return;
        }
        try {
            Connection conn = connections.get();
            String recordSql = "UPDATE " + target.getTable()
                    + " SET base_updated_at = ?, mongo_id = COALESCE(mongo_id, ?), synced = TRUE WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(recordSql)) {
                stmt.setString(1, updatedAt);
                stmt.setString(2, mongoId);
                stmt.setString(3, recordId);
                stmt.executeUpdate();
            }
            String pendingSql = "DELETE FROM pending_changes "
                    + "WHERE entity = ? AND record_id = ? AND occurred_at <= ?";
            try (PreparedStatement stmt = conn.prepareStatement(pendingSql)) {
                stmt.setString(1, entity);
                stmt.setString(2, recordId);
                stmt.setTimestamp(3, Timestamp.valueOf(sentOccurredAt));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            log("Impossible d'acquitter " + entity + "/" + recordId, e);
        }
    }

    /**
     * Retire une écriture en attente sans rien acquitter. Utilisé quand l'api
     * rejette un événement pour une raison d'autorisation (§4.1
     * {@code rejected[]}) : le rejouer ne pourra jamais réussir.
     */
    public void delete(String entity, String recordId) {
        String sql = "DELETE FROM pending_changes WHERE entity = ? AND record_id = ?";
        try {
            Connection conn = connections.get();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entity);
                stmt.setString(2, recordId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            log("Impossible de retirer l'écriture en attente " + entity + "/" + recordId, e);
        }
    }

    /**
     * L'état de synchronisation d'un enregistrement métier : son identifiant
     * serveur et son jeton de concurrence optimiste. Les deux sont
     * {@code null} tant que l'enregistrement n'a jamais été acquitté.
     *
     * <p>Vit ici plutôt que dans les modèles : c'est de la mécanique de sync,
     * l'UI n'en a rien à faire.</p>
     */
    public RecordSyncState findRecordSyncState(String entity, String recordId) {
        SyncEntity target = SyncEntity.fromWire(entity);
        if (target == null) {
            return new RecordSyncState(null, null);
        }
        String sql = "SELECT mongo_id, base_updated_at FROM " + target.getTable() + " WHERE id = ?";
        try {
            Connection conn = connections.get();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, recordId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new RecordSyncState(rs.getString("mongo_id"), rs.getString("base_updated_at"));
                    }
                }
            }
        } catch (SQLException e) {
            log("Impossible de lire l'état de sync de " + entity + "/" + recordId, e);
        }
        return new RecordSyncState(null, null);
    }

    private Optional<PendingChange> findByKey(Connection conn, String entity, String recordId) throws SQLException {
        String sql = "SELECT * FROM pending_changes WHERE entity = ? AND record_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entity);
            stmt.setString(2, recordId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(extract(rs)) : Optional.empty();
            }
        }
    }

    private PendingChange extract(ResultSet rs) throws SQLException {
        PendingChange change = new PendingChange();
        change.setId(rs.getLong("id"));
        change.setEntity(rs.getString("entity"));
        change.setRecordId(rs.getString("record_id"));
        change.setOperation(rs.getString("operation"));
        change.setMongoId(rs.getString("mongo_id"));
        change.setPayload(rs.getString("payload"));
        change.setBaseUpdatedAt(rs.getString("base_updated_at"));

        Timestamp occurredAt = rs.getTimestamp("occurred_at");
        if (occurredAt != null) {
            change.setOccurredAt(occurredAt.toLocalDateTime());
        }
        return change;
    }

    private void log(String message, Exception e) {
        java.util.logging.Logger.getLogger(PendingChangesRepository.class.getName())
                .log(java.util.logging.Level.WARNING, message, e);
    }

    /**
     * Source de connexion, pour pouvoir viser une base en mémoire dans les tests.
     */
    @FunctionalInterface
    public interface ConnectionSupplier {
        Connection get() throws SQLException;
    }

    /**
     * @param mongoId       identifiant serveur, {@code null} avant le premier ack
     * @param baseUpdatedAt jeton de concurrence optimiste, {@code null} avant le premier ack
     */
    public record RecordSyncState(String mongoId, String baseUpdatedAt) {
    }
}
