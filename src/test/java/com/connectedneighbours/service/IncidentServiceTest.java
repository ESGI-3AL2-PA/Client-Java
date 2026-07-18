package com.connectedneighbours.service;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentServiceTest {

    private IncidentRepository repository;
    private IncidentService service;

    @BeforeEach
    void setUp() {
        repository = mock(IncidentRepository.class);
        service = new IncidentService(repository);
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
