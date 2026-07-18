package com.connectedneighbours.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncStatusTest {

    @Test
    void hasFiveValues() {
        assertEquals(5, SyncStatus.values().length);
    }

    @Test
    void containsAllExpectedValues() {
        assertNotNull(SyncStatus.valueOf("OFFLINE"));
        assertNotNull(SyncStatus.valueOf("SYNCING"));
        assertNotNull(SyncStatus.valueOf("SUCCESS"));
        assertNotNull(SyncStatus.valueOf("ERROR"));
        assertNotNull(SyncStatus.valueOf("AUTH_REQUIRED"));
    }
}
