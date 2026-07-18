package com.connectedneighbours.config;

import com.connectedneighbours.model.Incident;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JacksonConfigTest {

    @Test
    void get_returnsSameInstance() {
        ObjectMapper m1 = JacksonConfig.get();
        ObjectMapper m2 = JacksonConfig.get();
        assertSame(m1, m2);
    }

    @Test
    void serializesLocalDateTimeAsIsoString() throws JsonProcessingException {
        ObjectMapper mapper = JacksonConfig.get();
        Incident incident = new Incident();
        incident.setId("inc-1");
        LocalDateTime date = LocalDateTime.of(2025, 7, 1, 12, 0, 0);
        incident.setCreatedAt(date);
        incident.setUpdatedAt(date);

        String json = mapper.writeValueAsString(incident);

        assertTrue(json.contains("\"createdAt\""));
        assertTrue(json.contains("2025-07-01T12:00:00"));
    }

    @Test
    void deserializesIsoDateString() throws JsonProcessingException {
        ObjectMapper mapper = JacksonConfig.get();
        String json = "{\"id\":\"inc-1\",\"createdAt\":\"2025-07-01T12:00:00\"}";

        Incident incident = mapper.readValue(json, Incident.class);
        assertEquals("inc-1", incident.getId());
        assertEquals(LocalDateTime.of(2025, 7, 1, 12, 0, 0), incident.getCreatedAt());
    }

    @Test
    void excludesNullFields() throws JsonProcessingException {
        ObjectMapper mapper = JacksonConfig.get();
        Incident incident = new Incident();
        incident.setId("inc-1");
        incident.setDescription("Test");

        String json = mapper.writeValueAsString(incident);

        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains("\"description\""));
        assertFalse(json.contains("\"photoUrl\""));
        assertFalse(json.contains("\"category\""));
    }

    @Test
    void ignoresUnknownProperties() throws JsonProcessingException {
        ObjectMapper mapper = JacksonConfig.get();
        String json = "{\"id\":\"inc-1\",\"unknownField\":\"someValue\"}";

        Incident incident = mapper.readValue(json, Incident.class);
        assertEquals("inc-1", incident.getId());
    }
}
