package com.connectedneighbours.service;

import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.StatisticRepository;
import com.connectedneighbours.repository.SyncApiClient;
import com.connectedneighbours.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calcule les statistiques du quartier et les écrit dans la table
 * {@code statistics} historisée, que l'écran Statistiques et l'export lisent
 * déjà.
 *
 * <p>Incidents et utilisateurs descendent intégralement dans la base locale :
 * leur total est calculé depuis H2 (§9.5), ce qui marche hors-ligne et ne
 * peut pas diverger de ce que l'opérateur a sous les yeux.</p>
 *
 * <p>Annonces, événements et votes n'ont pas de table locale — leur total est
 * lu directement sur l'api à chaque cycle. {@link #recompute()} n'est appelé
 * que quand {@code SyncService} a déjà vérifié la connectivité, donc l'appel
 * réseau est sans risque ; un échec ponctuel (ex. 401) est tracé et laisse
 * simplement la mesure du jour absente, sans faire échouer les autres
 * métriques.</p>
 *
 * <p>Ce sont des statistiques <em>de quartier</em> : le flux étant limité au
 * quartier de l'appelant (§5.5), la base locale ne contient que celui-ci — ce
 * qui correspond à ce que le même admin voit sur le web.</p>
 */
public class StatisticsService {

    private static final Logger LOGGER = Logger.getLogger(StatisticsService.class.getName());
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IncidentRepository incidentRepo;
    private final UserRepository userRepo;
    private final StatisticRepository statisticRepo;
    private final SyncApiClient apiClient;
    private final ObjectMapper mapper;

    public StatisticsService(SyncApiClient apiClient) {
        this(new IncidentRepository(), new UserRepository(), new StatisticRepository(), apiClient);
    }

    public StatisticsService(IncidentRepository incidentRepo, UserRepository userRepo,
                             StatisticRepository statisticRepo, SyncApiClient apiClient) {
        this.incidentRepo = incidentRepo;
        this.userRepo = userRepo;
        this.statisticRepo = statisticRepo;
        this.apiClient = apiClient;
        this.mapper = JacksonConfig.get();
    }

    /**
     * Recalcule et enregistre les métriques du jour.
     */
    public void recompute() {
        String period = LocalDate.now().format(PERIOD_FMT);
        List<Incident> incidents = incidentRepo.findAll();

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("incidents.total", (double) incidents.size());
        metrics.put("users.total", (double) userRepo.findAll().size());

        Map<String, Double> byStatus = new LinkedHashMap<>();
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (Incident incident : incidents) {
            increment(byStatus, incident.getStatus());
            increment(byCategory, incident.getCategory());
        }
        byStatus.forEach((status, count) -> metrics.put("incidents.status." + status, count));
        byCategory.forEach((category, count) -> metrics.put("incidents.category." + category, count));

        fetchRemoteTotal(metrics, "/listings?page=1&limit=1", "listings.total");
        fetchRemoteTotal(metrics, "/events?page=1&limit=1", "events.total");
        fetchRemoteTotal(metrics, "/votes?page=1&limit=1", "votes.total");

        metrics.forEach((key, value) -> upsert(key, value, period));
    }

    /**
     * Lit {@code total} sur un endpoint paginé de l'api. En cas d'échec, la
     * métrique est simplement omise de ce cycle — {@code recompute()}
     * continue avec les autres.
     */
    private void fetchRemoteTotal(Map<String, Double> metrics, String endpoint, String metricKey) {
        try {
            JsonNode root = mapper.readTree(apiClient.get(endpoint));
            metrics.put(metricKey, root.path("total").asDouble(0));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Échec de la récupération de la statistique depuis " + endpoint, e);
        }
    }

    private void increment(Map<String, Double> counts, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        counts.merge(key, 1.0, Double::sum);
    }

    /**
     * Une seule mesure par (métrique, période) : la journée en cours est
     * réécrite, les jours passés restent pour la tendance.
     */
    private void upsert(String metricKey, double value, String period) {
        List<Statistic> existing = statisticRepo.findByMetricKeyAndPeriod(metricKey, period);
        if (existing.isEmpty()) {
            statisticRepo.save(new Statistic(metricKey, value, period));
            return;
        }
        Statistic statistic = existing.get(0);
        statistic.setMetricValue(value);
        statistic.setRecordedAt(LocalDateTime.now());
        statisticRepo.update(statistic);
    }
}
