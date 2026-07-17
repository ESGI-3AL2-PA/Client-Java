package com.connectedneighbours.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    @Test
    void hasFiveValues() {
        assertEquals(5, Page.values().length);
    }

    @Test
    void containsAllPages() {
        assertNotNull(Page.valueOf("DASHBOARD"));
        assertNotNull(Page.valueOf("INCIDENTS"));
        assertNotNull(Page.valueOf("USERS"));
        assertNotNull(Page.valueOf("STATISTICS"));
        assertNotNull(Page.valueOf("SETTINGS"));
    }
}
