package com.connectedneighbours.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SyncLogTest {

    @Test
    void defaultConstructor_createsEmptySyncLog() {
        SyncLog log = new SyncLog();
        assertNull(log.getId());
        assertNull(log.getTableName());
        assertNull(log.getRecordId());
        assertNull(log.getAction());
        assertNull(log.getConflict());
    }

    @Test
    void parameterizedConstructor_setsFieldsAndDefaults() {
        SyncLog log = new SyncLog("incidents", "inc-1", "UPDATE");

        assertEquals("incidents", log.getTableName());
        assertEquals("inc-1", log.getRecordId());
        assertEquals("UPDATE", log.getAction());
        assertFalse(log.getConflict());
        assertNotNull(log.getSyncedAt());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        SyncLog log = new SyncLog();
        LocalDateTime now = LocalDateTime.now();

        log.setId(1);
        log.setTableName("users");
        log.setRecordId("user-1");
        log.setAction("CREATE");
        log.setConflict(true);
        log.setSyncedAt(now);

        assertEquals(1, log.getId());
        assertEquals("users", log.getTableName());
        assertEquals("user-1", log.getRecordId());
        assertEquals("CREATE", log.getAction());
        assertTrue(log.getConflict());
        assertEquals(now, log.getSyncedAt());
    }

    @Test
    void toString_containsTableAndAction() {
        SyncLog log = new SyncLog("incidents", "inc-1", "DELETE");
        String str = log.toString();
        assertTrue(str.contains("incidents"));
        assertTrue(str.contains("inc-1"));
        assertTrue(str.contains("DELETE"));
    }
}
