package com.connectedneighbours.controller;

import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.repository.StatisticRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.*;

/**
 * Écran des statistiques, alimenté par la table {@code statistics} que
 * {@link com.connectedneighbours.service.StatisticsService} recalcule
 * localement depuis H2 à chaque cycle de synchronisation.
 *
 * <p>Ce sont des statistiques <em>de quartier</em> : la base locale ne reçoit
 * que le quartier de l'opérateur (§5.5 du design offline-sync). Les métriques
 * qui n'ont pas de source locale (annonces, événements, votes) ne sont plus
 * affichées — elles restaient vides depuis l'abandon de l'agrégation
 * serveur.</p>
 */
public class StatisticsController {
    private final StatisticRepository statisticRepo = new StatisticRepository();
    @FXML
    private Label statUsersTotal;
    @FXML
    private Label statIncidentsTotal;
    @FXML
    private Label statUsersTrend;
    @FXML
    private Label statIncidentsTrend;
    @FXML
    private PieChart incidentsByStatusChart;
    @FXML
    private PieChart incidentsByCategoryChart;

    @FXML
    public void initialize() {
        List<Statistic> all = statisticRepo.findAll();
        Map<String, List<Statistic>> history = historyByMetricKey(all);

        setTotal(statUsersTotal, statUsersTrend, history.get("users.total"));
        setTotal(statIncidentsTotal, statIncidentsTrend, history.get("incidents.total"));

        populatePieChart(incidentsByStatusChart, history, "incidents.status.");
        populatePieChart(incidentsByCategoryChart, history, "incidents.category.");
    }

    @FXML
    public void onCloseClick() {
        Stage stage = (Stage) statUsersTotal.getScene().getWindow();
        stage.close();
    }

    private Map<String, List<Statistic>> historyByMetricKey(List<Statistic> all) {
        Map<String, List<Statistic>> byKey = new HashMap<>();
        for (Statistic s : all) {
            byKey.computeIfAbsent(s.getMetricKey(), k -> new ArrayList<>()).add(s);
        }
        for (List<Statistic> history : byKey.values()) {
            history.sort(Comparator.comparing(
                    Statistic::getRecordedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            ));
        }
        return byKey;
    }

    public void setTotal(Label valueLabel, Label trendLabel, List<Statistic> history) {
        if (history == null || history.isEmpty()) {
            valueLabel.setText("_");
            trendLabel.setText("_");
            return;
        }

        Statistic latest = history.get(history.size() - 1);
        valueLabel.setText(String.valueOf(latest.getMetricValue().intValue()));

        if (history.size() < 2) {
            trendLabel.setText("Première mesure");
            return;
        }

        Statistic previous = history.get(history.size() - 2);
        int delta = latest.getMetricValue().intValue() - previous.getMetricValue().intValue();

        if (delta > 0) {
            trendLabel.setText("" + delta + " depuis la dernier sync");
        } else if (delta < 0) {
            trendLabel.setText(delta + " depuis la dernier sync");
        } else {
            trendLabel.setText("Stable");
        }


    }

    private void populatePieChart(PieChart chart, Map<String, List<Statistic>> history, String prefix) {
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();

        for (Map.Entry<String, List<Statistic>> entry : history.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                List<Statistic> values = entry.getValue();
                if (values == null || values.isEmpty()) {
                    continue;
                }

                Statistic latest = values.get(values.size() - 1);
                Double value = latest.getMetricValue();

                if (value != null) {
                    String sliceName = entry.getKey().substring(prefix.length());
                    data.add(new PieChart.Data(sliceName, value));
                }
            }
        }

        chart.setData(data);
    }


}