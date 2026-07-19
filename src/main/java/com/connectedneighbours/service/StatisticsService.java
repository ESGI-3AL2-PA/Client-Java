package com.connectedneighbours.service;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.StatisticRepository;
import com.connectedneighbours.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcule les statistiques <em>localement</em>, depuis H2 (§9.5).
 *
 * <p>Puisque les incidents et les utilisateurs descendent désormais
 * intégralement dans la base locale, il n'y a plus de raison d'appeler une
 * agrégation serveur : le calcul local marche hors-ligne et ne peut pas
 * diverger de ce que l'opérateur a sous les yeux.</p>
 *
 * <p>Ce sont des statistiques <em>de quartier</em> : le flux étant limité au
 * quartier de l'appelant (§5.5), la base locale ne contient que celui-ci — ce
 * qui correspond à ce que le même admin voit sur le web.</p>
 *
 * <p>Les résultats sont écrits dans la table {@code statistics} historisée,
 * que l'écran Statistiques et l'export lisent déjà.</p>
 */
public class StatisticsService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IncidentRepository incidentRepo;
    private final UserRepository userRepo;
    private final StatisticRepository statisticRepo;

    public StatisticsService() {
        this(new IncidentRepository(), new UserRepository(), new StatisticRepository());
    }

    public StatisticsService(IncidentRepository incidentRepo, UserRepository userRepo,
                             StatisticRepository statisticRepo) {
        this.incidentRepo = incidentRepo;
        this.userRepo = userRepo;
        this.statisticRepo = statisticRepo;
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

        metrics.forEach((key, value) -> upsert(key, value, period));
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
