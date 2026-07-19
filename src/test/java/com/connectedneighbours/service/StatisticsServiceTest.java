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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    private IncidentRepository incidentRepo;
    private UserRepository userRepo;
    private StatisticRepository statisticRepo;
    private SyncApiClient apiClient;
    private StatisticsService service;

    private static Incident incident(String id, String districtId, String status, String category) {
        Incident i = new Incident();
        i.setId(id);
        i.setDistrictId(districtId);
        i.setStatus(status);
        i.setCategory(category);
        return i;
    }

    private static User user(String id, String districtId) {
        User u = new User();
        u.setId(id);
        u.setDistrictId(districtId);
        return u;
    }

    @BeforeEach
    void setUp() {
        incidentRepo = mock(IncidentRepository.class);
        userRepo = mock(UserRepository.class);
        statisticRepo = mock(StatisticRepository.class);
        apiClient = mock(SyncApiClient.class);
        // Aucune mesure existante pour ce jour : upsert() passera toujours par save().
        when(statisticRepo.findByMetricKeyAndPeriodAndDistrict(anyString(), anyString(), any()))
                .thenReturn(List.of());
        service = new StatisticsService(incidentRepo, userRepo, statisticRepo, apiClient);
    }

    private List<Statistic> capturedSaves() {
        ArgumentCaptor<Statistic> captor = ArgumentCaptor.forClass(Statistic.class);
        verify(statisticRepo, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    /** Valeur d'une métrique dans une série donnée ({@code null} = agrégat). */
    private double valueFor(List<Statistic> values, String key, String districtId) {
        return values.stream()
                .filter(s -> key.equals(s.getMetricKey()))
                .filter(s -> districtId == null ? s.getDistrictId() == null : districtId.equals(s.getDistrictId()))
                .map(Statistic::getMetricValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric: " + key + " / " + districtId));
    }

    private List<String> keysOf(List<Statistic> values, String districtId) {
        return values.stream()
                .filter(s -> districtId == null ? s.getDistrictId() == null : districtId.equals(s.getDistrictId()))
                .map(Statistic::getMetricKey)
                .toList();
    }

    //  Totaux locaux + distants

    @Test
    void recompute_writesLocalAndRemoteTotals() throws IOException {
        when(incidentRepo.findAll()).thenReturn(List.of(incident("i1", "d1", "open", "voirie")));
        when(userRepo.findAll()).thenReturn(List.of(user("u1", "d1"), user("u2", "d1"), user("u3", "d1")));
        when(apiClient.get("/listings?page=1&limit=1")).thenReturn("{\"total\":5}");
        when(apiClient.get("/events?page=1&limit=1")).thenReturn("{\"total\":2}");
        when(apiClient.get("/votes?page=1&limit=1")).thenReturn("{\"total\":7}");

        service.recompute();

        List<Statistic> saved = capturedSaves();
        assertEquals(1.0, valueFor(saved, "incidents.total", null));
        assertEquals(3.0, valueFor(saved, "users.total", null));
        assertEquals(5.0, valueFor(saved, "listings.total", null));
        assertEquals(2.0, valueFor(saved, "events.total", null));
        assertEquals(7.0, valueFor(saved, "votes.total", null));
    }

    @Test
    void recompute_remoteFailure_omitsThatMetricButKeepsOthers() throws IOException {
        when(incidentRepo.findAll()).thenReturn(List.of());
        when(userRepo.findAll()).thenReturn(List.of(new User()));
        when(apiClient.get("/listings?page=1&limit=1")).thenReturn("{\"total\":4}");
        when(apiClient.get("/events?page=1&limit=1")).thenThrow(new IOException("boom"));
        when(apiClient.get("/votes?page=1&limit=1")).thenReturn("{\"total\":1}");

        assertDoesNotThrow(() -> service.recompute());

        List<String> keys = keysOf(capturedSaves(), null);
        assertTrue(keys.contains("listings.total"));
        assertTrue(keys.contains("votes.total"));
        assertFalse(keys.contains("events.total"));
    }

    /**
     * Les endpoints distants sont interrogés sans filtre de quartier : rattacher
     * leur total à chaque série ferait revendiquer le total global par tous les
     * quartiers. Ils n'appartiennent donc qu'à l'agrégat.
     */
    @Test
    void recompute_remoteTotalsAreOnlyWrittenToTheAggregateSeries() throws IOException {
        when(incidentRepo.findAll()).thenReturn(List.of(
                incident("i1", "d1", "open", "voirie"),
                incident("i2", "d2", "open", "bruit")
        ));
        when(userRepo.findAll()).thenReturn(List.of());
        when(apiClient.get("/listings?page=1&limit=1")).thenReturn("{\"total\":5}");
        when(apiClient.get("/events?page=1&limit=1")).thenReturn("{\"total\":2}");
        when(apiClient.get("/votes?page=1&limit=1")).thenReturn("{\"total\":7}");

        service.recompute();
        List<Statistic> saved = capturedSaves();

        assertEquals(5.0, valueFor(saved, "listings.total", null));
        assertFalse(keysOf(saved, "d1").contains("listings.total"));
        assertFalse(keysOf(saved, "d2").contains("listings.total"));
    }

    /** Un seul appel par endpoint, quel que soit le nombre de quartiers. */
    @Test
    void recompute_queriesEachRemoteEndpointOnce() throws IOException {
        when(incidentRepo.findAll()).thenReturn(List.of(
                incident("i1", "d1", "open", "voirie"),
                incident("i2", "d2", "open", "bruit")
        ));
        when(userRepo.findAll()).thenReturn(List.of());
        when(apiClient.get(anyString())).thenReturn("{\"total\":1}");

        service.recompute();

        verify(apiClient).get(eq("/listings?page=1&limit=1"));
        verify(apiClient).get(eq("/events?page=1&limit=1"));
        verify(apiClient).get(eq("/votes?page=1&limit=1"));
    }

    //  Dimension quartier

    /**
     * Le cas qui motive la dimension : chez un superAdmin la base porte plusieurs
     * quartiers, et un total unique ne décrit alors aucun quartier réel.
     */
    @Test
    void recompute_writesOneSeriesPerDistrictPlusTheAggregate() {
        when(incidentRepo.findAll()).thenReturn(List.of(
                incident("i1", "d1", "open", "voirie"),
                incident("i2", "d1", "open", "voirie"),
                incident("i3", "d2", "closed", "bruit")
        ));
        when(userRepo.findAll()).thenReturn(List.of(user("u1", "d1"), user("u2", "d2"), user("u3", "d2")));

        service.recompute();
        List<Statistic> stats = capturedSaves();

        assertEquals(3.0, valueFor(stats, "incidents.total", null));
        assertEquals(2.0, valueFor(stats, "incidents.total", "d1"));
        assertEquals(1.0, valueFor(stats, "incidents.total", "d2"));

        assertEquals(3.0, valueFor(stats, "users.total", null));
        assertEquals(1.0, valueFor(stats, "users.total", "d1"));
        assertEquals(2.0, valueFor(stats, "users.total", "d2"));
    }

    @Test
    void recompute_breaksDownStatusAndCategoryPerDistrict() {
        when(incidentRepo.findAll()).thenReturn(List.of(
                incident("i1", "d1", "open", "voirie"),
                incident("i2", "d2", "closed", "bruit")
        ));
        when(userRepo.findAll()).thenReturn(List.of());

        service.recompute();
        List<Statistic> stats = capturedSaves();

        assertEquals(1.0, valueFor(stats, "incidents.status.open", "d1"));
        assertEquals(1.0, valueFor(stats, "incidents.category.voirie", "d1"));
        assertEquals(1.0, valueFor(stats, "incidents.status.closed", "d2"));

        // La série d1 ne doit pas hériter des catégories de d2.
        assertFalse(keysOf(stats, "d1").contains("incidents.category.bruit"));
    }

    /** Chez un admin mono-quartier, série quartier et agrégat coïncident. */
    @Test
    void recompute_singleDistrict_aggregateMatchesTheDistrictSeries() {
        when(incidentRepo.findAll()).thenReturn(List.of(incident("i1", "d1", "open", "voirie")));
        when(userRepo.findAll()).thenReturn(List.of(user("u1", "d1")));

        service.recompute();
        List<Statistic> stats = capturedSaves();

        assertEquals(valueFor(stats, "incidents.total", null), valueFor(stats, "incidents.total", "d1"));
    }

    /**
     * Un quartier vidé n'est pas réécrit à zéro : son historique doit rester lisible
     * plutôt que d'afficher une chute qui n'a pas eu lieu.
     */
    @Test
    void recompute_withNoLocalData_writesOnlyTheAggregate() {
        when(incidentRepo.findAll()).thenReturn(List.of());
        when(userRepo.findAll()).thenReturn(List.of());

        service.recompute();

        assertTrue(capturedSaves().stream().allMatch(s -> s.getDistrictId() == null));
    }

    @Test
    void recompute_ignoresBlankDistrictIds() {
        when(incidentRepo.findAll()).thenReturn(List.of(incident("i1", "  ", "open", "voirie")));
        when(userRepo.findAll()).thenReturn(List.of());

        service.recompute();

        assertTrue(capturedSaves().stream().allMatch(s -> s.getDistrictId() == null));
    }

    @Test
    void recompute_existingMetric_isUpdatedNotDuplicated() {
        Statistic existing = new Statistic("incidents.total", 99.0, "2026-07-19", "d1");
        existing.setId(7);
        when(incidentRepo.findAll()).thenReturn(List.of(incident("i1", "d1", "open", "voirie")));
        when(userRepo.findAll()).thenReturn(List.of());
        // Tous matchers : Mockito interdit de mélanger valeurs brutes et matchers.
        when(statisticRepo.findByMetricKeyAndPeriodAndDistrict(eq("incidents.total"), anyString(), eq("d1")))
                .thenReturn(List.of(existing));

        service.recompute();

        verify(statisticRepo).update(existing);
        assertEquals(1.0, existing.getMetricValue());
    }
}
