package com.connectedneighbours.service;

import com.connectedneighbours.auth.exception.TokenUnavailableException;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.config.SyncConfig;
import com.connectedneighbours.model.PendingChange;
import com.connectedneighbours.repository.ApiException;
import com.connectedneighbours.repository.DistrictRepository;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.PendingChangesRepository;
import com.connectedneighbours.repository.SyncApiClient;
import com.connectedneighbours.repository.UserRepository;
import com.connectedneighbours.sync.ChangeEntry;
import com.connectedneighbours.sync.IngestEvent;
import com.connectedneighbours.sync.IngestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SyncServiceTest {

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 7, 18, 10, 0);

    private SyncApiClient apiClient;
    private PendingChangesRepository pendingRepo;
    private IncidentRepository incidentRepo;
    private UserRepository userRepo;
    private DistrictRepository districtRepo;
    private StatisticsService statisticsService;
    private SyncConfig syncConfig;
    private List<SyncStatus> statuses;

    private static PendingChange pending(long id, String operation, String mongoId, String baseUpdatedAt) {
        return pending(id, "inc-1", operation, mongoId, baseUpdatedAt);
    }

    private static PendingChange pending(long id, String recordId, String operation,
                                         String mongoId, String baseUpdatedAt) {
        PendingChange change = new PendingChange();
        change.setId(id);
        change.setEntity("incident");
        change.setRecordId(recordId);
        change.setOperation(operation);
        change.setMongoId(mongoId);
        change.setPayload("{\"category\":\"voirie\"}");
        change.setBaseUpdatedAt(baseUpdatedAt);
        change.setOccurredAt(SENT_AT);
        return change;
    }

    private static IngestResult.AppliedEvent applied(long id, String mongoId, String updatedAt) {
        IngestResult.AppliedEvent event = new IngestResult.AppliedEvent();
        event.setId(id);
        event.setMongoId(mongoId);
        event.setOperation(PendingChange.INSERT);
        event.setUpdatedAt(updatedAt);
        return event;
    }

    private static IngestResult.ConflictAck conflict(long id, String conflictId) {
        IngestResult.ConflictAck ack = new IngestResult.ConflictAck();
        ack.setId(id);
        ack.setMongoId("mongo-1");
        ack.setConflictId(conflictId);
        return ack;
    }

    private static IngestResult.RejectedEvent rejected(long id, String reason) {
        IngestResult.RejectedEvent event = new IngestResult.RejectedEvent();
        event.setId(id);
        event.setReason(reason);
        return event;
    }

    @BeforeEach
    void setUp() {
        apiClient = mock(SyncApiClient.class);
        pendingRepo = mock(PendingChangesRepository.class);
        incidentRepo = mock(IncidentRepository.class);
        userRepo = mock(UserRepository.class);
        districtRepo = mock(DistrictRepository.class);
        statisticsService = mock(StatisticsService.class);
        syncConfig = mock(SyncConfig.class);
        statuses = new ArrayList<>();
    }

    private SyncService service(ConnectivityChecker connectivity) {
        SyncService service = new SyncService(
                apiClient,
                pendingRepo,
                incidentRepo,
                userRepo,
                districtRepo,
                statisticsService,
                syncConfig,
                JacksonConfig.get(),
                connectivity,
                Runnable::run
        );
        service.setStatusListener(statuses::add);
        return service;
    }

    private SyncService onlineService() {
        return service(() -> true);
    }

    @Test
    void syncCycle_offline_notifiesOfflineAndSkipsWork() {
        service(() -> false).syncCycle();

        assertEquals(List.of(SyncStatus.OFFLINE), statuses);
        verifyNoInteractions(apiClient, pendingRepo);
    }

    @Test
    void syncCycle_nothingToDo_reportsSuccess() throws Exception {
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of());
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        onlineService().syncCycle();

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.SUCCESS), statuses);
        verify(apiClient, never()).ingest(any());
        verify(statisticsService).recompute();
    }

    @Test
    void push_appliedEvent_advancesBaseFromAckAndClearsRow() throws Exception {
        PendingChange change = pending(42L, PendingChange.INSERT, null, null);
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(change));

        IngestResult result = new IngestResult();
        result.setApplied(List.of(applied(42L, "mongo-1", "2026-07-18T10:00:00.123Z")));
        when(apiClient.ingest(any())).thenReturn(result);
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        onlineService().syncCycle();

        verify(pendingRepo).setRecordMongoId("incident", "inc-1", "mongo-1");
        // Le jeton vient de l'ack, pas d'un écho du flux.
        verify(pendingRepo).advanceBaseAndClear(
                "incident", "inc-1", "mongo-1", "2026-07-18T10:00:00.123Z", SENT_AT);
        verify(pendingRepo, never()).delete(anyString(), anyString());
    }

    @Test
    void push_insertEvent_omitsOptimisticConcurrencyToken() throws Exception {
        PendingChange change = pending(42L, PendingChange.INSERT, null, null);
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(change));
        when(apiClient.ingest(any())).thenReturn(new IngestResult());
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        onlineService().syncCycle();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IngestEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(apiClient).ingest(captor.capture());

        IngestEvent sent = captor.getValue().get(0);
        assertEquals(42L, sent.getId());
        assertEquals("incident", sent.getEntity());
        assertNull(sent.getBaseUpdatedAt());
        assertEquals("voirie", sent.getData().get("category"));
    }

    @Test
    void push_updateEvent_carriesOptimisticConcurrencyToken() throws Exception {
        PendingChange change = pending(43L, PendingChange.UPDATE, "mongo-1", "2026-07-17T09:00:00.000Z");
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(change));
        when(apiClient.ingest(any())).thenReturn(new IngestResult());
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        onlineService().syncCycle();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IngestEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(apiClient).ingest(captor.capture());

        assertEquals("2026-07-17T09:00:00.000Z", captor.getValue().get(0).getBaseUpdatedAt());
    }

    @Test
    void push_conflictedEvent_keepsPendingRowAndRaisesBadge() throws Exception {
        PendingChange change = pending(71L, PendingChange.UPDATE, "mongo-1", "stale");
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(change));

        IngestResult result = new IngestResult();
        result.setConflicts(List.of(conflict(71L, "c-uuid")));
        when(apiClient.ingest(any())).thenReturn(result);
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        List<Integer> badges = new ArrayList<>();
        SyncService service = onlineService();
        service.setConflictListener(badges::add);

        service.syncCycle();

        // Rien n'a été écrit côté serveur : l'édition locale doit survivre,
        // c'est le pull qui nettoiera après résolution.
        verify(pendingRepo, never()).delete(anyString(), anyString());
        verify(pendingRepo, never()).advanceBaseAndClear(
                anyString(), anyString(), any(), any(), any());
        assertEquals(List.of(1), badges);
        assertEquals(1, service.getPendingConflictCount());
    }

    @Test
    void push_rejectedEvent_dropsPendingRowAndSurfacesError() throws Exception {
        PendingChange change = pending(88L, PendingChange.UPDATE, "mongo-1", "base");
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(change));

        IngestResult result = new IngestResult();
        result.setRejected(List.of(rejected(88L, "out-of-district")));
        when(apiClient.ingest(any())).thenReturn(result);
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        List<List<String>> reported = new ArrayList<>();
        SyncService service = onlineService();
        service.setRejectionListener(reported::add);

        service.syncCycle();

        // Refus d'autorisation : rejouer ne pourra jamais aboutir.
        verify(pendingRepo).delete("incident", "inc-1");
        verify(pendingRepo, never()).advanceBaseAndClear(
                anyString(), anyString(), any(), any(), any());
        assertEquals(1, reported.size());
        assertTrue(reported.get(0).get(0).contains("hors de votre quartier"));
    }

    @Test
    void push_rejectedAsUnprocessable_isDroppedLikeAnyOtherRefusal() throws Exception {
        PendingChange change = pending(90L, PendingChange.UPDATE, null, "base");
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(change));

        IngestResult result = new IngestResult();
        result.setRejected(List.of(rejected(90L, "unprocessable")));
        when(apiClient.ingest(any())).thenReturn(result);
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        List<List<String>> reported = new ArrayList<>();
        SyncService service = onlineService();
        service.setRejectionListener(reported::add);

        service.syncCycle();

        verify(pendingRepo).delete("incident", "inc-1");
        assertTrue(reported.get(0).get(0).contains("impossible à appliquer"));
    }

    @Test
    void push_mixedBatch_settlesEachRowByItsOwnOutcome() throws Exception {
        PendingChange ok = pending(1L, "inc-ok", PendingChange.INSERT, null, null);
        PendingChange clashing = pending(2L, "inc-conflict", PendingChange.UPDATE, "mongo-2", "stale");
        PendingChange refused = pending(3L, "inc-rejected", PendingChange.UPDATE, "mongo-3", "base");
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(ok, clashing, refused));

        // Comptabilité totale : les trois ids soumis reviennent, chacun dans
        // exactement une liste.
        IngestResult result = new IngestResult();
        result.setApplied(List.of(applied(1L, "mongo-1", "2026-07-18T10:00:00.123Z")));
        result.setConflicts(List.of(conflict(2L, "c-uuid")));
        result.setRejected(List.of(rejected(3L, "out-of-district")));
        when(apiClient.ingest(any())).thenReturn(result);
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        List<Integer> badges = new ArrayList<>();
        List<List<String>> reported = new ArrayList<>();
        SyncService service = onlineService();
        service.setConflictListener(badges::add);
        service.setRejectionListener(reported::add);

        service.syncCycle();

        // Appliqué → ligne purgée, jeton avancé depuis l'ack.
        verify(pendingRepo).setRecordMongoId("incident", "inc-ok", "mongo-1");
        verify(pendingRepo).advanceBaseAndClear(
                "incident", "inc-ok", "mongo-1", "2026-07-18T10:00:00.123Z", SENT_AT);

        // En conflit → ligne conservée, badge levé.
        verify(pendingRepo, never()).delete("incident", "inc-conflict");
        verify(pendingRepo, never()).advanceBaseAndClear(
                eq("incident"), eq("inc-conflict"), any(), any(), any());
        assertEquals(List.of(1), badges);

        // Refusé → ligne lâchée, erreur remontée, aucun acquittement.
        verify(pendingRepo).delete("incident", "inc-rejected");
        verify(pendingRepo, never()).advanceBaseAndClear(
                eq("incident"), eq("inc-rejected"), any(), any(), any());
        assertEquals(1, reported.size());
        assertTrue(reported.get(0).get(0).contains("inc-rejected"));

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.SUCCESS), statuses);
    }

    @Test
    void push_eventMissingFromAck_leavesTheRowAloneWithoutCrashing() throws Exception {
        PendingChange stranded = pending(7L, "inc-stranded", PendingChange.UPDATE, "mongo-7", "base");
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of(stranded));

        // Violation de l'invariant côté serveur : l'id soumis ne revient dans
        // aucune des trois listes.
        when(apiClient.ingest(any())).thenReturn(new IngestResult());
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        onlineService().syncCycle();

        // On ne devine pas : ni purge ni acquittement, et le cycle aboutit.
        verify(pendingRepo, never()).delete(anyString(), anyString());
        verify(pendingRepo, never()).advanceBaseAndClear(
                anyString(), anyString(), any(), any(), any());
        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.SUCCESS), statuses);
    }

    @Test
    void pull_dispatchesByEntityAndAdvancesCursor() throws Exception {
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of());

        ChangeEntry incident = new ChangeEntry();
        incident.setIndex(152L);
        incident.setEntity("incident");
        incident.setOperation(PendingChange.INSERT);
        incident.setMongoId("mongo-9");
        incident.setData(Map.of("category", "voirie", "updatedAt", "2026-07-18T10:00:00.000Z"));

        ChangeEntry district = new ChangeEntry();
        district.setIndex(153L);
        district.setEntity("district");
        district.setOperation(PendingChange.INSERT);
        district.setMongoId("d-1");
        district.setData(Map.of("name", "Centre"));

        when(apiClient.changes(0L, 200)).thenReturn(List.of(incident, district));

        onlineService().syncCycle();

        verify(incidentRepo).saveFromSync(any(), eq("mongo-9"), eq("2026-07-18T10:00:00.000Z"));
        // Les quartiers descendent sans jeton de concurrence : ils n'ont pas
        // d'updatedAt et ne remontent jamais.
        verify(districtRepo).saveFromSync(any(), eq("d-1"));
        verify(syncConfig).setCursor(153L);
    }

    @Test
    void pull_deleteEntry_removesLocalRecord() throws Exception {
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of());

        ChangeEntry entry = new ChangeEntry();
        entry.setIndex(160L);
        entry.setEntity("user");
        entry.setOperation(PendingChange.DELETE);
        entry.setMongoId("mongo-u1");

        when(apiClient.changes(0L, 200)).thenReturn(List.of(entry));

        onlineService().syncCycle();

        verify(userRepo).deleteFromSync("mongo-u1");
    }

    @Test
    void syncCycle_tokenExpired_reportsAuthRequired() throws Exception {
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of());
        when(apiClient.changes(anyLong(), anyInt()))
                .thenThrow(new TokenUnavailableException(new IOException("expiré")));

        onlineService().syncCycle();

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.AUTH_REQUIRED), statuses);
    }

    @Test
    void syncCycle_unauthorized_reportsAuthRequired() throws Exception {
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of());
        when(apiClient.changes(anyLong(), anyInt()))
                .thenThrow(new ApiException(401, "Unauthorized"));

        onlineService().syncCycle();

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.AUTH_REQUIRED), statuses);
    }

    @Test
    void syncCycle_serverError_reportsError() throws Exception {
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of());
        when(apiClient.changes(anyLong(), anyInt()))
                .thenThrow(new ApiException(500, "Boom"));

        onlineService().syncCycle();

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.ERROR), statuses);
    }

    @Test
    void syncCycle_reentrant_secondCycleIsSkipped() throws Exception {
        when(pendingRepo.findBatch(anyInt())).thenReturn(List.of());
        when(apiClient.changes(anyLong(), anyInt())).thenReturn(List.of());

        SyncService[] holder = new SyncService[1];
        // Le second appel survient pendant que le premier tourne : seul celui
        // qui a gagné le compareAndSet doit travailler.
        holder[0] = service(() -> {
            holder[0].syncCycle();
            return true;
        });

        holder[0].syncCycle();

        assertEquals(List.of(SyncStatus.SYNCING, SyncStatus.SUCCESS), statuses);
        verify(statisticsService).recompute();
    }
}
