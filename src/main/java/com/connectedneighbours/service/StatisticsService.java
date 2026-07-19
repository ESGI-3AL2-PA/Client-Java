package com.connectedneighbours.service;

import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.model.User;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p>Les métriques portent une dimension quartier. Un admin ne reçoit que le
 * sien (§5.5) et n'y voit donc rien de neuf ; un superAdmin reçoit tous les
 * quartiers, et c'est cette dimension qui permet à l'écran de suivre le
 * sélecteur plutôt que d'afficher un total inter-quartiers. Les totaux distants
 * ci-dessus, eux, ne sont pas filtrables : ils ne sont écrits que dans la série
 * agrégée ({@code districtId} nul).</p>
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
     * <p>
     * Une série par quartier présent en base, plus une série {@code null}
     * tous-quartiers confondus. Chez un admin la base ne contient que son
     * quartier, les deux séries coïncident donc ; chez un superAdmin, dont la
     * base porte tous les quartiers, c'est ce qui permet à l'écran de suivre le
     * sélecteur au lieu d'agréger un total qui ne veut rien dire.
     */
    public void recompute() {
        String period = LocalDate.now().format(PERIOD_FMT);
        List<Incident> incidents = incidentRepo.findAll();
        List<User> users = userRepo.findAll();

        recomputeScope(period, null, incidents, users);

        for (String districtId : districtsPresentIn(incidents, users)) {
            recomputeScope(
                    period,
                    districtId,
                    incidents.stream().filter(i -> districtId.equals(i.getDistrictId())).toList(),
                    users.stream().filter(u -> districtId.equals(u.getDistrictId())).toList()
            );
        }
    }

    /**
     * Quartiers à mesurer. Un quartier n'ayant plus ni incident ni habitant
     * n'est pas recalculé : son historique reste tel quel plutôt que d'être
     * réécrit à zéro, ce qui se lirait comme une chute réelle.
     */
    private Set<String> districtsPresentIn(List<Incident> incidents, List<User> users) {
        Set<String> districtIds = new LinkedHashSet<>();
        incidents.forEach(i -> addIfPresent(districtIds, i.getDistrictId()));
        users.forEach(u -> addIfPresent(districtIds, u.getDistrictId()));
        return districtIds;
    }

    private void addIfPresent(Set<String> target, String districtId) {
        if (districtId != null && !districtId.isBlank()) {
            target.add(districtId);
        }
    }

    private void recomputeScope(String period, String districtId, List<Incident> incidents, List<User> users) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("incidents.total", (double) incidents.size());
        metrics.put("users.total", (double) users.size());

        Map<String, Double> byStatus = new LinkedHashMap<>();
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (Incident incident : incidents) {
            increment(byStatus, incident.getStatus());
            increment(byCategory, incident.getCategory());
        }
        byStatus.forEach((status, count) -> metrics.put("incidents.status." + status, count));
        byCategory.forEach((category, count) -> metrics.put("incidents.category." + category, count));

        // Les totaux distants ne portent pas de quartier : les endpoints sont
        // interrogés sans filtre. Ils n'appartiennent donc qu'à la série agrégée —
        // les rattacher à chaque quartier ferait revendiquer le total global par
        // tous, ce qui est faux dès qu'il y a plus d'un quartier.
        if (districtId == null) {
            fetchRemoteTotal(metrics, "/listings?page=1&limit=1", "listings.total");
            fetchRemoteTotal(metrics, "/events?page=1&limit=1", "events.total");
            fetchRemoteTotal(metrics, "/votes?page=1&limit=1", "votes.total");
        }

        metrics.forEach((key, value) -> upsert(key, value, period, districtId));
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
     * Une seule mesure par (métrique, période, quartier) : la journée en cours
     * est réécrite, les jours passés restent pour la tendance.
     */
    private void upsert(String metricKey, double value, String period, String districtId) {
        List<Statistic> existing =
                statisticRepo.findByMetricKeyAndPeriodAndDistrict(metricKey, period, districtId);
        if (existing.isEmpty()) {
            statisticRepo.save(new Statistic(metricKey, value, period, districtId));
            return;
        }
        Statistic statistic = existing.get(0);
        statistic.setMetricValue(value);
        statistic.setRecordedAt(LocalDateTime.now());
        statisticRepo.update(statistic);
    }
}
