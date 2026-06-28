package com.connectedneighbours.controller;

import javafx.fxml.FXML;

public class DashboardController {

    @FXML
    public void onIncidentsClick() {
        System.out.println("→ Incidents cliqué");
    }

    @FXML
    public void onStatisticsClick() {
        System.out.println("→ Statistiques cliqué");
    }
}
