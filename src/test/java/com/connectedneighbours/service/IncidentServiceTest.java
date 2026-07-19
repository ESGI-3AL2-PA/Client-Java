package com.connectedneighbours.service;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.PendingChange;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.PendingChangesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentServiceTest {

    private IncidentRepository repository;
    private PendingChangesRepository pendingRepo;
    private IncidentService service;

    @BeforeEach
    void setUp() {
        repository = mock(IncidentRepository.class);
        pendingRepo = mock(PendingChangesRepository.class);
        // Par défaut : jamais acquitté par le serveur. Les tests qui portent
        // sur un enregistrement déjà synchronisé le redéfinissent.
        when(pendingRepo.findRecordSyncState(anyString(), anyString()))
                .thenReturn(new PendingChangesRepository.RecordSyncState(null, null));
        service = new IncidentService(repository, pendingRepo);
    }

    /**
     * Convention inverse de {@code StatisticRepository.findByDistrictId(null)}, qui
     * cible la série {@code districtId IS NULL}. Les deux sont corrects pour leurs
     * données — ce test épingle la différence pour qu'on ne les « unifie » pas.
     */
    @Test
    void getIncidentsByDistrict_nullMeansEveryDistrict() {
        service.getIncidentsByDistrict(null);

        verify(repository).findAll();
        verify(repository, never()).findByDistrictId(anyString());
    }

    @Test
    void getIncidentsByDistrict_filtersOnTheGivenDistrict() {
        service.getIncidentsByDistrict("d1");

        verify(repository).findByDistrictId("d1");
        verify(repository, never()).findAll();
    }

    /**
     * Sans quartier l'incident est refusé au push (out-of-district) et n'apparaît
     * sous aucune sélection : il est perdu des deux côtés.
     */
    @Test
    void createIncident_stampsTheGivenDistrict() {
        Incident created = service.createIncident("voirie", "nid de poule", "u1", "d1");

        assertEquals("d1", created.getDistrictId());
    }

    @Test
    void createIncident_withoutDistrict_keepsTheLegacyEmptyValue() {
        Incident created = service.createIncident("voirie", "nid de poule", "u1");

        assertEquals("", created.getDistrictId());
    }

    @Test
    void createIncident_queuesInsertForPush() {
        Incident created = service.createIncident("voirie", "nid de poule", "u1");

        verify(repository).save(created);
        verify(pendingRepo).upsert(
                eq("incident"), eq(created.getId()), eq(PendingChange.INSERT),
                isNull(), anyString(), isNull());
    }

    @Test
    void updateIncident_neverAcknowledged_staysAnInsert() {
        Incident incident = new Incident("i1", "r1", "d1", "cat", "desc");
        when(pendingRepo.findRecordSyncState("incident", "i1"))
                .thenReturn(new PendingChangesRepository.RecordSyncState(null, null));

        service.updateIncident(incident);

        // Sans mongo_id, le serveur ignore encore cet incident : le pousser en
        // UPDATE n'aurait rien à viser.
        verify(pendingRepo).upsert(
                eq("incident"), eq("i1"), eq(PendingChange.INSERT),
                isNull(), anyString(), isNull());
    }

    @Test
    void updateIncident_alreadySynced_queuesUpdateWithBaseToken() {
        Incident incident = new Incident("i1", "r1", "d1", "cat", "desc");
        when(pendingRepo.findRecordSyncState("incident", "i1"))
                .thenReturn(new PendingChangesRepository.RecordSyncState("mongo-1", "2026-07-17T09:00:00.000Z"));

        service.updateIncident(incident);

        verify(pendingRepo).upsert(
                eq("incident"), eq("i1"), eq(PendingChange.UPDATE),
                eq("mongo-1"), anyString(), eq("2026-07-17T09:00:00.000Z"));
    }

    @Test
    void deleteIncident_queuesDeleteWithoutPayload() {
        when(pendingRepo.findRecordSyncState("incident", "i1"))
                .thenReturn(new PendingChangesRepository.RecordSyncState("mongo-1", "base"));

        service.deleteIncident("i1");

        verify(repository).delete("i1");
        verify(pendingRepo).upsert(
                eq("incident"), eq("i1"), eq(PendingChange.DELETE),
                eq("mongo-1"), isNull(), eq("base"));
    }

    @Test
    void getAllIncidents_delegatesToRepository() {
        Incident inc = new Incident("i1", "r1", "d1", "cat", "desc");
        when(repository.findAll()).thenReturn(List.of(inc));

        List<Incident> result = service.getAllIncidents();
        assertEquals(1, result.size());
        assertEquals("i1", result.get(0).getId());
        verify(repository).findAll();
    }

    @Test
    void getIncidentById_found() {
        Incident inc = new Incident("i1", "r1", "d1", "cat", "desc");
        when(repository.findById("i1")).thenReturn(Optional.of(inc));

        Optional<Incident> result = service.getIncidentById("i1");
        assertTrue(result.isPresent());
        assertEquals("i1", result.get().getId());
    }

    @Test
    void getIncidentById_notFound() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        Optional<Incident> result = service.getIncidentById("unknown");
        assertTrue(result.isEmpty());
    }

    @Test
    void getIncidentsByStatus_enum() {
        Incident inc = new Incident("i1", "r1", "d1", "cat", "desc");
        when(repository.findByStatus(Incident.Status.OPEN)).thenReturn(List.of(inc));

        List<Incident> result = service.getIncidentsByStatus(Incident.Status.OPEN);
        assertEquals(1, result.size());
        verify(repository).findByStatus(Incident.Status.OPEN);
    }

    @Test
    void getIncidentsByStatus_string() {
        Incident inc = new Incident("i1", "r1", "d1", "cat", "desc");
        when(repository.findByStatus("in_progress")).thenReturn(List.of(inc));

        List<Incident> result = service.getIncidentsByStatus("in_progress");
        assertEquals(1, result.size());
    }

    @Test
    void getDistinctCategories_returnsSortedUniqueCategories() {
        Incident inc1 = new Incident("i1", "r1", "d1", "Bruit", "desc");
        Incident inc2 = new Incident("i2", "r1", "d1", "Vandalisme", "desc");
        Incident inc3 = new Incident("i3", "r1", "d1", "Bruit", "desc");
        when(repository.findAll()).thenReturn(List.of(inc1, inc2, inc3));

        List<String> categories = service.getDistinctCategories();
        assertEquals(2, categories.size());
        assertEquals("Bruit", categories.get(0));
        assertEquals("Vandalisme", categories.get(1));
    }

    @Test
    void getDistinctCategories_filtersNullAndBlank() {
        Incident inc1 = new Incident("i1", "r1", "d1", null, "desc");
        Incident inc2 = new Incident("i2", "r1", "d1", "  ", "desc");
        Incident inc3 = new Incident("i3", "r1", "d1", "Bruit", "desc");
        when(repository.findAll()).thenReturn(List.of(inc1, inc2, inc3));

        List<String> categories = service.getDistinctCategories();
        assertEquals(1, categories.size());
        assertEquals("Bruit", categories.get(0));
    }

    @Test
    void countByStatus_delegatesToRepository() {
        when(repository.countByStatus("open")).thenReturn(5);

        int count = service.countByStatus(Incident.Status.OPEN);
        assertEquals(5, count);
    }

    @Test
    void countUnsynced_delegatesToRepository() {
        when(repository.countUnsynced()).thenReturn(3);

        int count = service.countUnsynced();
        assertEquals(3, count);
    }

    @Test
    void createIncident_validatesAndSaves() {
        Incident saved = service.createIncident("Bruit", "Trop de bruit la nuit", "user-1");

        assertNotNull(saved.getId());
        assertEquals("Bruit", saved.getCategory());
        assertEquals("Trop de bruit la nuit", saved.getDescription());
        assertEquals("user-1", saved.getReporterId());
        assertEquals(Incident.Status.OPEN.getValue(), saved.getStatus());
        assertFalse(saved.isSynced());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        verify(repository).save(any(Incident.class));
    }

    @Test
    void createIncident_nullReporter_usesUnknown() {
        Incident saved = service.createIncident("Bruit", "Description", null);

        assertEquals("unknown", saved.getReporterId());
    }

    @Test
    void createIncident_nullCategory_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createIncident(null, "desc", "user-1"));
    }

    @Test
    void createIncident_blankCategory_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createIncident("  ", "desc", "user-1"));
    }

    @Test
    void createIncident_nullDescription_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createIncident("Bruit", null, "user-1"));
    }

    @Test
    void createIncident_blankDescription_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createIncident("Bruit", "  ", "user-1"));
    }

    @Test
    void updateIncident_setsTimestampAndSyncedFalse() {
        Incident incident = new Incident("i1", "r1", "d1", "cat", "desc");
        incident.setSynced(true);

        service.updateIncident(incident);

        assertFalse(incident.isSynced());
        assertNotNull(incident.getUpdatedAt());
        verify(repository).update(incident);
    }

    @Test
    void updateIncident_nullIncident_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.updateIncident(null));
    }

    @Test
    void updateIncident_nullId_throwsException() {
        Incident incident = new Incident();
        assertThrows(IllegalArgumentException.class, () -> service.updateIncident(incident));
    }

    @Test
    void deleteIncident_validId_deletes() {
        service.deleteIncident("i1");
        verify(repository).delete("i1");
    }

    @Test
    void deleteIncident_nullId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteIncident(null));
    }

    @Test
    void deleteIncident_blankId_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteIncident("  "));
    }
}
