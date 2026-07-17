package com.connectedneighbours.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AlertTest {

    @Test
    void defaultConstructor_createsEmptyAlert() {
        Alert alert = new Alert();
        assertNull(alert.getId());
        assertNull(alert.getType());
        assertNull(alert.getMessage());
        assertFalse(alert.isRead());
        assertNull(alert.getCreatedAt());
    }

    @Test
    void parameterizedConstructor_setsTypeAndMessage() {
        Alert alert = new Alert("info", "Un nouvel incident a été créé");

        assertEquals("info", alert.getType());
        assertEquals("Un nouvel incident a été créé", alert.getMessage());
        assertNull(alert.getId());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        Alert alert = new Alert();
        LocalDateTime now = LocalDateTime.now();

        alert.setId("alert-1");
        alert.setType("warning");
        alert.setMessage("Conflit de synchronisation");
        alert.setRead(true);
        alert.setCreatedAt(now);

        assertEquals("alert-1", alert.getId());
        assertEquals("warning", alert.getType());
        assertEquals("Conflit de synchronisation", alert.getMessage());
        assertTrue(alert.isRead());
        assertEquals(now, alert.getCreatedAt());
    }
}
