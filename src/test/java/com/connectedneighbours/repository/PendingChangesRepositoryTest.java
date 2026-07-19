package com.connectedneighbours.repository;

import com.connectedneighbours.model.PendingChange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests contre une base H2 en mémoire, dédiée à chaque test — le schéma vient
 * des mêmes migrations que la base réelle.
 */
class PendingChangesRepositoryTest {

    private Connection connection;
    private PendingChangesRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        String url = "jdbc:h2:mem:pending-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        connection = DriverManager.getConnection(url, "sa", "");
        DatabaseManager.initSchema(connection);
        repo = new PendingChangesRepository(() -> connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    private void insertIncident(String id) throws SQLException {
        String sql = "INSERT INTO incidents (id, reporterId, districtId, category, description) "
                + "VALUES (?, 'r1', 'd1', 'voirie', 'nid de poule')";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        }
    }

    private String incidentColumn(String id, String column) throws SQLException {
        String sql = "SELECT " + column + " FROM incidents WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Test
    void upsert_thenFindBatch_returnsTheQueuedWrite() {
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"category\":\"voirie\"}", null);

        List<PendingChange> batch = repo.findBatch(10);

        assertEquals(1, batch.size());
        PendingChange change = batch.get(0);
        assertEquals("incident", change.getEntity());
        assertEquals("inc-1", change.getRecordId());
        assertEquals(PendingChange.INSERT, change.getOperation());
        assertEquals("{\"category\":\"voirie\"}", change.getPayload());
        assertNull(change.getMongoId());
    }

    @Test
    void upsert_twiceOnSameRecord_keepsOneRow() {
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);
        repo.upsert("incident", "inc-1", PendingChange.UPDATE, null, "{\"v\":2}", null);

        assertEquals(1, repo.count());
    }

    @Test
    void upsert_updateAfterUnsyncedInsert_staysAnInsert() {
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);
        repo.upsert("incident", "inc-1", PendingChange.UPDATE, null, "{\"v\":2}", null);

        PendingChange change = repo.findByKey("incident", "inc-1").orElseThrow();
        // Le serveur n'a jamais vu cet enregistrement : la charge fraîche part
        // en création, pas en mise à jour.
        assertEquals(PendingChange.INSERT, change.getOperation());
        assertEquals("{\"v\":2}", change.getPayload());
    }

    @Test
    void upsert_deleteAfterUnsyncedInsert_cancelsTheWriteEntirely() {
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);
        repo.upsert("incident", "inc-1", PendingChange.DELETE, null, null, null);

        // Créé puis supprimé hors-ligne : le serveur n'a rien à en savoir.
        assertEquals(0, repo.count());
    }

    @Test
    void upsert_deleteAfterSyncedRecord_queuesTheDelete() {
        repo.upsert("incident", "inc-1", PendingChange.UPDATE, "mongo-1", "{\"v\":1}", "base");
        repo.upsert("incident", "inc-1", PendingChange.DELETE, "mongo-1", null, "base");

        PendingChange change = repo.findByKey("incident", "inc-1").orElseThrow();
        assertEquals(PendingChange.DELETE, change.getOperation());
        assertEquals("mongo-1", change.getMongoId());
        assertNull(change.getPayload());
    }

    @Test
    void setRecordMongoId_stampsBothTheRecordAndTheQueuedWrite() throws SQLException {
        insertIncident("inc-1");
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);

        repo.setRecordMongoId("incident", "inc-1", "mongo-1");

        assertEquals("mongo-1", incidentColumn("inc-1", "mongo_id"));
        assertEquals("TRUE", incidentColumn("inc-1", "synced").toUpperCase());
        assertEquals("mongo-1", repo.findByKey("incident", "inc-1").orElseThrow().getMongoId());
    }

    @Test
    void advanceBaseAndClear_writesTheAckedTokenAndDropsTheRow() throws SQLException {
        insertIncident("inc-1");
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);
        LocalDateTime sentAt = repo.findByKey("incident", "inc-1").orElseThrow().getOccurredAt();

        repo.advanceBaseAndClear("incident", "inc-1", "mongo-1", "2026-07-18T10:00:00.123Z", sentAt);

        assertEquals("2026-07-18T10:00:00.123Z", incidentColumn("inc-1", "base_updated_at"));
        assertEquals("mongo-1", incidentColumn("inc-1", "mongo_id"));
        assertEquals(0, repo.count());
    }

    @Test
    void advanceBaseAndClear_rowReDirtiedMidFlight_isKept() throws SQLException {
        insertIncident("inc-1");
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);

        // L'opérateur a re-modifié l'enregistrement pendant le push : la ligne
        // porte un horodatage postérieur à celui qui a été envoyé.
        LocalDateTime sentAt = repo.findByKey("incident", "inc-1").orElseThrow()
                .getOccurredAt().minusSeconds(5);

        repo.advanceBaseAndClear("incident", "inc-1", "mongo-1", "2026-07-18T10:00:00.123Z", sentAt);

        assertEquals(1, repo.count());
        assertEquals("2026-07-18T10:00:00.123Z", incidentColumn("inc-1", "base_updated_at"));
    }

    @Test
    void delete_removesTheQueuedWriteWithoutTouchingTheRecord() throws SQLException {
        insertIncident("inc-1");
        repo.upsert("incident", "inc-1", PendingChange.UPDATE, "mongo-1", "{\"v\":1}", "base");

        repo.delete("incident", "inc-1");

        assertEquals(0, repo.count());
        assertTrue(repo.findByKey("incident", "inc-1").isEmpty());
        assertNull(incidentColumn("inc-1", "base_updated_at"));
    }

    @Test
    void findBatch_ordersOldestFirstAndHonoursTheLimit() {
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);
        repo.upsert("incident", "inc-2", PendingChange.INSERT, null, "{\"v\":2}", null);
        repo.upsert("incident", "inc-3", PendingChange.INSERT, null, "{\"v\":3}", null);

        List<PendingChange> batch = repo.findBatch(2);

        assertEquals(2, batch.size());
        assertEquals("inc-1", batch.get(0).getRecordId());
        assertEquals("inc-2", batch.get(1).getRecordId());
    }

    @Test
    void findRecordSyncState_unknownRecord_returnsEmptyState() {
        PendingChangesRepository.RecordSyncState state =
                repo.findRecordSyncState("incident", "nonexistent");

        assertNull(state.mongoId());
        assertNull(state.baseUpdatedAt());
    }

    @Test
    void findRecordSyncState_afterAck_returnsTheServerIdentity() throws SQLException {
        insertIncident("inc-1");
        repo.upsert("incident", "inc-1", PendingChange.INSERT, null, "{\"v\":1}", null);
        LocalDateTime sentAt = repo.findByKey("incident", "inc-1").orElseThrow().getOccurredAt();
        repo.advanceBaseAndClear("incident", "inc-1", "mongo-1", "2026-07-18T10:00:00.123Z", sentAt);

        PendingChangesRepository.RecordSyncState state =
                repo.findRecordSyncState("incident", "inc-1");

        assertEquals("mongo-1", state.mongoId());
        assertEquals("2026-07-18T10:00:00.123Z", state.baseUpdatedAt());
    }

    @Test
    void findByKey_unknownRecord_returnsEmpty() {
        Optional<PendingChange> change = repo.findByKey("incident", "nope");
        assertTrue(change.isEmpty());
    }
}
