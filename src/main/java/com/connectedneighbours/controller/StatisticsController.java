package com.connectedneighbours.controller;

import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.repository.StatisticRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatisticsController {
    @FXML private Label statUsersTotal;
    @FXML private Label statListingsTotal;
    @FXML private Label statEventsTotal;
    @FXML private Label statVotesTotal;
    @FXML private Label statIncidentsTotal;
    @FXML private PieChart incidentsByStatusChart;
    @FXML private PieChart incidentsByCategoryChart;

    private final StatisticRepository statisticRepo = new StatisticRepository();

    @FXML
    public void initialize() {
        List<Statistic> all = statisticRepo.findAll();
        Map<String, Statistic> latest = latestByMetricKey(all);

        setTotal(statUsersTotal, latest.get("users.total"));
        setTotal(statListingsTotal, latest.get("listings.total"));
        setTotal(statEventsTotal, latest.get("events.total"));
        setTotal(statVotesTotal, latest.get("votes.total"));
        setTotal(statIncidentsTotal, latest.get("incidents.total"));

        populatePieChart(incidentsByStatusChart, latest, "incidents.status.");
        populatePieChart(incidentsByCategoryChart, latest, "incidents.category.");
    }

    @FXML
    public void onCloseClick() {
        Stage stage = (Stage) statUsersTotal.getScene().getWindow();
        stage.close();
    }

    private Map<String ,Statistic> latestByMetricKey(List<Statistic> all) {
        Map<String, Statistic> latest = new HashMap<>();
        for(Statistic s : all) {
            Statistic current = latest.get(s.getMetricKey());
            boolean isNewer = current == null
                    || current.getRecordedAt() == null
                    || (s.getRecordedAt() != null && s.getRecordedAt().isAfter(current.getRecordedAt()));
            if(isNewer) {
                latest.put(s.getMetricKey(), s);
            }
        }
        return latest;
    }

    public void setTotal(Label label, Statistic stat) {
        if(stat == null || stat.getMetricKey() == null) {
            label.setText("_");
        } else {
            label.setText(String.valueOf(stat.getMetricValue().intValue()));
        }
    }

    private void populatePieChart(PieChart chart, Map<String, Statistic> latest, String prefix) {
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        for (Map.Entry<String, Statistic> entry : latest.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String sliceName = entry.getKey().substring(prefix.length());
                Double value = entry.getValue().getMetricValue();
                if (value != null) {
                    data.add(new PieChart.Data(sliceName, value));
                }
            }
        }
        chart.setData(data);
    }


}