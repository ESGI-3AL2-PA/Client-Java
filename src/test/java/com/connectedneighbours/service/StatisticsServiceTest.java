package com.connectedneighbours.service;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.StatisticRepository;
import com.connectedneighbours.repository.SyncApiClient;
import com.connectedneighbours.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsServiceTest {

    private IncidentRepository incidentRepo;
    private UserRepository userRepo;
    private StatisticRepository statisticRepo;
    private SyncApiClient apiClient;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        incidentRepo = mock(IncidentRepository.class);
        userRepo = mock(UserRepository.class);
        statisticRepo = mock(StatisticRepository.class);
        apiClient = mock(SyncApiClient.class);
        // Aucune mesure existante pour ce jour : upsert() passera toujours par save().
        when(statisticRepo.findByMetricKeyAndPeriod(anyString(), anyString())).thenReturn(List.of());
        service = new StatisticsService(incidentRepo, userRepo, statisticRepo, apiClient);
    }

    @Test
    void recompute_writesLocalAndRemoteTotals() throws IOException {
        when(incidentRepo.findAll()).thenReturn(List.of(new Incident("i1", "r1", "d1", "voirie", "desc")));
        when(userRepo.findAll()).thenReturn(List.of(new User(), new User(), new User()));
        when(apiClient.get("/listings?page=1&limit=1")).thenReturn("{\"total\":5}");
        when(apiClient.get("/events?page=1&limit=1")).thenReturn("{\"total\":2}");
        when(apiClient.get("/votes?page=1&limit=1")).thenReturn("{\"total\":7}");

        service.recompute();

        List<Statistic> saved = capturedSaves();
        assertEquals(1.0, valueFor(saved, "incidents.total"));
        assertEquals(3.0, valueFor(saved, "users.total"));
        assertEquals(5.0, valueFor(saved, "listings.total"));
        assertEquals(2.0, valueFor(saved, "events.total"));
        assertEquals(7.0, valueFor(saved, "votes.total"));
    }

    @Test
    void recompute_remoteFailure_omitsThatMetricButKeepsOthers() throws IOException {
        when(incidentRepo.findAll()).thenReturn(List.of());
        when(userRepo.findAll()).thenReturn(List.of(new User()));
        when(apiClient.get("/listings?page=1&limit=1")).thenReturn("{\"total\":4}");
        when(apiClient.get("/events?page=1&limit=1")).thenThrow(new IOException("boom"));
        when(apiClient.get("/votes?page=1&limit=1")).thenReturn("{\"total\":1}");

        assertDoesNotThrow(() -> service.recompute());

        List<Statistic> saved = capturedSaves();
        List<String> keys = saved.stream().map(Statistic::getMetricKey).toList();

        assertTrue(keys.contains("listings.total"));
        assertTrue(keys.contains("votes.total"));
        assertFalse(keys.contains("events.total"));
    }

    private List<Statistic> capturedSaves() {
        ArgumentCaptor<Statistic> captor = ArgumentCaptor.forClass(Statistic.class);
        verify(statisticRepo, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private double valueFor(List<Statistic> values, String key) {
        return values.stream()
                .filter(s -> s.getMetricKey().equals(key))
                .map(Statistic::getMetricValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric: " + key));
    }
}
