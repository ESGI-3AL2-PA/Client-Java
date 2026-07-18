package com.connectedneighbours.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentTest {

    @Test
    void defaultConstructor_createsEmptyIncident() {
        Incident incident = new Incident();
        assertNull(incident.getId());
        assertFalse(incident.isSynced());
    }

    @Test
    void parameterizedConstructor_setsFieldsAndDefaults() {
        Incident incident = new Incident("inc-1", "rep-1", "dist-1", "Vandalisme", "Description");

        assertEquals("inc-1", incident.getId());
        assertEquals("rep-1", incident.getReporterId());
        assertEquals("dist-1", incident.getDistrictId());
        assertEquals("Vandalisme", incident.getCategory());
        assertEquals("Description", incident.getDescription());
        assertEquals(Incident.Status.OPEN.getValue(), incident.getStatus());
        assertFalse(incident.isSynced());
        assertNotNull(incident.getCreatedAt());
        assertNotNull(incident.getUpdatedAt());
    }

    @Test
    void gettersAndSetters_workCorrectly() {
        Incident incident = new Incident();
        LocalDateTime now = LocalDateTime.now();
        List<IncidentHistory> history = new ArrayList<>();

        incident.setId("inc-1");
        incident.setReporterId("rep-1");
        incident.setDistrictId("dist-1");
        incident.setCategory("Bruit");
        incident.setDescription("Trop de bruit");
        incident.setPhotoUrl("http://photo.url");
        incident.setStatus(Incident.Status.IN_PROGRESS.getValue());
        incident.setAssignedTo("admin-1");
        incident.setCreatedAt(now);
        incident.setUpdatedAt(now);
        incident.setHistory(history);
        incident.setSynced(true);

        assertEquals("inc-1", incident.getId());
        assertEquals("rep-1", incident.getReporterId());
        assertEquals("dist-1", incident.getDistrictId());
        assertEquals("Bruit", incident.getCategory());
        assertEquals("Trop de bruit", incident.getDescription());
        assertEquals("http://photo.url", incident.getPhotoUrl());
        assertEquals(Incident.Status.IN_PROGRESS.getValue(), incident.getStatus());
        assertEquals("admin-1", incident.getAssignedTo());
        assertEquals(now, incident.getCreatedAt());
        assertEquals(now, incident.getUpdatedAt());
        assertEquals(history, incident.getHistory());
        assertTrue(incident.isSynced());
    }

    @Test
    void statusEnum_hasCorrectValues() {
        assertEquals("open", Incident.Status.OPEN.getValue());
        assertEquals("Ouvert", Incident.Status.OPEN.getLabel());
        assertEquals("in_progress", Incident.Status.IN_PROGRESS.getValue());
        assertEquals("En cours", Incident.Status.IN_PROGRESS.getLabel());
        assertEquals("resolved", Incident.Status.RESOLVED.getValue());
        assertEquals("Résolu", Incident.Status.RESOLVED.getLabel());
        assertEquals("closed", Incident.Status.CLOSED.getValue());
        assertEquals("Fermé", Incident.Status.CLOSED.getLabel());
        assertEquals("unsynced", Incident.Status.UNSYNCED.getValue());
        assertEquals("Non Syncronisé", Incident.Status.UNSYNCED.getLabel());
    }

    @Test
    void statusEnum_hasFiveValues() {
        assertEquals(5, Incident.Status.values().length);
    }

    @Test
    void byCreatedAtAsc_sortsAscending() {
        Incident older = new Incident();
        older.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        Incident newer = new Incident();
        newer.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        assertTrue(Incident.BY_CREATED_AT_ASC.compare(older, newer) < 0);
        assertTrue(Incident.BY_CREATED_AT_ASC.compare(newer, older) > 0);
    }

    @Test
    void byCreatedAtDesc_sortsDescending() {
        Incident older = new Incident();
        older.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        Incident newer = new Incident();
        newer.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        assertTrue(Incident.BY_CREATED_AT_DESC.compare(older, newer) > 0);
        assertTrue(Incident.BY_CREATED_AT_DESC.compare(newer, older) < 0);
    }

    @Test
    void byCreatedAtAsc_handlesNullDates() {
        Incident withDate = new Incident();
        withDate.setCreatedAt(LocalDateTime.now());
        Incident withoutDate = new Incident();

        assertTrue(Incident.BY_CREATED_AT_ASC.compare(withoutDate, withDate) > 0);
        assertTrue(Incident.BY_CREATED_AT_ASC.compare(withDate, withoutDate) < 0);
    }

    @Test
    void toString_containsKeyFields() {
        Incident incident = new Incident("inc-1", "rep-1", "dist-1", "Vandalisme", "Description");
        String str = incident.toString();
        assertTrue(str.contains("inc-1"));
        assertTrue(str.contains("Vandalisme"));
    }
}
