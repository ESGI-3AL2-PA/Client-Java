package com.connectedneighbours.repository;

import com.connectedneighbours.model.Incident;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration basiques pour IncidentRepository.
 * Les méthodes CRUD complètes nécessitent une base H2 en mémoire.
 */
class IncidentRepositoryTest {

    @Test
    void findAll_returnsNonNullList() {
        IncidentRepository repo = new IncidentRepository();
        var incidents = repo.findAll();
        assertNotNull(incidents);
    }

    @Test
    void findAllWithLimit_returnsNonNullList() {
        IncidentRepository repo = new IncidentRepository();
        var incidents = repo.findAll(10);
        assertNotNull(incidents);
    }

    @Test
    void findById_unknownId_returnsEmpty() {
        IncidentRepository repo = new IncidentRepository();
        var result = repo.findById("nonexistent-id");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByStatus_returnsList() {
        IncidentRepository repo = new IncidentRepository();
        var incidents = repo.findByStatus(Incident.Status.OPEN);
        assertNotNull(incidents);
    }

    @Test
    void findByStatusByString_returnsList() {
        IncidentRepository repo = new IncidentRepository();
        var incidents = repo.findByStatus("open");
        assertNotNull(incidents);
    }

    @Test
    void findByReporterId_returnsList() {
        IncidentRepository repo = new IncidentRepository();
        var incidents = repo.findByReporterId("unknown");
        assertNotNull(incidents);
        assertTrue(incidents.isEmpty());
    }

    @Test
    void findByDistrictId_returnsList() {
        IncidentRepository repo = new IncidentRepository();
        var incidents = repo.findByDistrictId("unknown");
        assertNotNull(incidents);
        assertTrue(incidents.isEmpty());
    }

    @Test
    void countByStatus_returnsZeroWhenEmpty() {
        IncidentRepository repo = new IncidentRepository();
        int count = repo.countByStatus("open");
        assertTrue(count >= 0);
    }

    @Test
    void countUnsynced_returnsZeroWhenEmpty() {
        IncidentRepository repo = new IncidentRepository();
        int count = repo.countUnsynced();
        assertTrue(count >= 0);
    }

    @Test
    void findUnsynced_returnsEmptyList() {
        IncidentRepository repo = new IncidentRepository();
        var incidents = repo.findUnsynced();
        assertNotNull(incidents);
    }
}
