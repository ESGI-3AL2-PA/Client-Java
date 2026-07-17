package com.connectedneighbours.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class IncidentHistoryTest {

    @Test
    void defaultConstructor_createsEmptyHistory() {
        IncidentHistory history = new IncidentHistory();
        assertNull(history.getStatus());
        assertNull(history.getNote());
        assertNull(history.getUpdatedBy());
        assertNull(history.getUpdatedAt());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        IncidentHistory history = new IncidentHistory();
        LocalDateTime now = LocalDateTime.now();

        history.setStatus("open");
        history.setNote("Incident créé");
        history.setUpdatedBy("user-1");
        history.setUpdatedAt(now);

        assertEquals("open", history.getStatus());
        assertEquals("Incident créé", history.getNote());
        assertEquals("user-1", history.getUpdatedBy());
        assertEquals(now, history.getUpdatedAt());
    }

    @Test
    void toString_containsStatus() {
        IncidentHistory history = new IncidentHistory();
        history.setStatus("resolved");
        history.setNote("Résolu par admin");

        String str = history.toString();
        assertTrue(str.contains("resolved"));
        assertTrue(str.contains("Résolu par admin"));
    }
}
