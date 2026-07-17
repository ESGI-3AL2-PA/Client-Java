package com.connectedneighbours.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class StatisticTest {

    @Test
    void defaultConstructor_createsEmptyStatistic() {
        Statistic stat = new Statistic();
        assertNull(stat.getId());
        assertNull(stat.getMetricKey());
        assertNull(stat.getMetricValue());
        assertNull(stat.getPeriod());
        assertNull(stat.getRecordedAt());
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        Statistic stat = new Statistic("users.total", 150.0, "2025-01-01");

        assertEquals("users.total", stat.getMetricKey());
        assertEquals(150.0, stat.getMetricValue());
        assertEquals("2025-01-01", stat.getPeriod());
        assertNotNull(stat.getRecordedAt());
    }

    @Test
    void settersAndGetters_workCorrectly() {
        Statistic stat = new Statistic();
        LocalDateTime now = LocalDateTime.now();

        stat.setId(1);
        stat.setMetricKey("incidents.total");
        stat.setMetricValue(42.0);
        stat.setPeriod("2025-07-01");
        stat.setRecordedAt(now);

        assertEquals(1, stat.getId());
        assertEquals("incidents.total", stat.getMetricKey());
        assertEquals(42.0, stat.getMetricValue());
        assertEquals("2025-07-01", stat.getPeriod());
        assertEquals(now, stat.getRecordedAt());
    }

    @Test
    void toString_containsMetricKey() {
        Statistic stat = new Statistic("users.total", 100.0, "2025-01-01");
        String str = stat.toString();
        assertTrue(str.contains("users.total"));
        assertTrue(str.contains("100.0"));
    }
}
