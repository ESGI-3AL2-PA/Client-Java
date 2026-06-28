package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.model.Alert;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.AlertRepository;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.service.SyncService;
import com.connectedneighbours.service.SyncStatus;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    // Header
    @FXML
    private Button btnIncidents;
    @FXML
    private Button btnUsers;
    @FXML
    private Button btnStatistics;
    @FXML
    private Button btnSettings;
    @FXML
    private Button btnLogout;
    @FXML
    private Label currentUserLabel;
    //  Cartes de stats
    @FXML
    private Label statOpenIncidents;
    @FXML
    private Label statOpenTrend;
    @FXML
    private Label statInProgress;
    @FXML
    private Label statInProgressTrend;
    @FXML
    private Label statResolved;
    @FXML
    private Label statResolvedTrend;
    @FXML
    private Label statUnsynced;
    //  Tableau des incidents
    @FXML
    private TableView<Incident> incidentsTable;
    @FXML
    private TableColumn<Incident, String> colCategory;
    @FXML
    private TableColumn<Incident, String> colStatus;
    @FXML
    private TableColumn<Incident, java.time.LocalDateTime> colDate;
    //  Alertes
    @FXML
    private VBox alertsContainer;
    //  Barre de statut
    @FXML
    private Circle syncStatusDot;
    @FXML
    private Label syncStatusLabel;
    @FXML
    private Label lastSyncLabel;
    @FXML
    private Button syncNowButton;
    //  Services
    private AppContext appContext;
    private SyncService syncService;
    private IncidentRepository incidentRepo;
    private AlertRepository alertRepo;

    public DashboardController() {
    }

    public DashboardController(AppContext appContext, SyncService syncService) {
        this.appContext = appContext;
        this.syncService = syncService;
        this.incidentRepo = new IncidentRepository();
        this.alertRepo = new AlertRepository();
    }

    @FXML
    public void initialize() {
        if (incidentRepo == null) {
            incidentRepo = new IncidentRepository();
        }
        if (alertRepo == null) {
            alertRepo = new AlertRepository();
        }
        setupTable();
        loadData();
        if (syncService != null) {
            syncService.setStatusListener(this::updateSyncUI);
        }
        refreshCurrentUserLabel();
    }

    private void refreshCurrentUserLabel() {
        if (currentUserLabel == null) return;
        User u = appContext != null ? appContext.getCurrentUser() : null;
        String txt = (u == null) ? "" : (u.getFullName());
        currentUserLabel.setText(txt);
    }

    //  Config du TableView
    private void setupTable() {
        colCategory.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getCategory()));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Colonne date avec formatage
        colDate.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(java.time.LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(DATE_FMT));
                }
            }
        });

        // Colonne statut avec couleur
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                setStyle(switch (item) {
                    case "OPEN" -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case "IN_PROGRESS" -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                    case "RESOLVED" -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });

        // Double-clic → ouvrir le détail
        incidentsTable.setRowFactory(tv -> {
            TableRow<Incident> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    onIncidentDoubleClick(row.getItem());
            });
            return row;
        });
    }

    //  Chargement des données depuis H2 
    private void loadData() {
        try {
            List<Incident> incidents = incidentRepo.findAll();

            int open = incidentRepo.countByStatus(Incident.Status.OPEN.getValue());
            int inProgress = incidentRepo.countByStatus(Incident.Status.IN_PROGRESS.getValue());
            int resolved = incidentRepo.countByStatus(Incident.Status.RESOLVED.getValue());
            int unsynced = incidentRepo.countUnsynced();

            statOpenIncidents.setText(String.valueOf(open));
            statInProgress.setText(String.valueOf(inProgress));
            statResolved.setText(String.valueOf(resolved));
            statUnsynced.setText(String.valueOf(unsynced));

            // Tendances (comparaison simple)
            statOpenTrend.setText(open > 0 ? "Nécessite attention" : "Aucun incident");
            statResolvedTrend.setText("Ces 30 derniers jours");

            // Tableau : 10 incidents les plus récents
            incidentsTable.setItems(
                    FXCollections.observableArrayList(
                            incidents.stream()
                                    .sorted(Incident.BY_CREATED_AT_DESC)
                                    .limit(10)
                                    .toList()
                    )
            );

            // Alertes
            loadAlerts();

        } catch (SQLException e) {
            showError("Erreur chargement données : " + e.getMessage());
        }
    }

    //  Chargement des alertes 
    private void loadAlerts() throws SQLException {
        alertsContainer.getChildren().clear();

        List<Alert> alerts = alertRepo.findRecent(5);

        if (alerts.isEmpty()) {
            Label empty = new Label("Aucune alerte récente");
            empty.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
            alertsContainer.getChildren().add(empty);
            return;
        }

        for (Alert alert : alerts) {
            HBox alertBox = new HBox(8);
            alertBox.setStyle(
                    "-fx-background-color: " + alertBgColor(alert.getType()) + ";" +
                            "-fx-background-radius: 4; -fx-padding: 8;"
            );

            Label dot = new Label("●");
            dot.setStyle("-fx-text-fill: " + alertColor(alert.getType()) + "; -fx-font-size: 10px;");

            Label msg = new Label(alert.getMessage());
            msg.setWrapText(true);
            msg.setStyle("-fx-font-size: 12px;");
            msg.setMaxWidth(200);

            alertBox.getChildren().addAll(dot, msg);
            alertsContainer.getChildren().add(alertBox);
        }
    }

    //  Détail d'un incident (double-clic) 
    private void onIncidentDoubleClick(Incident incident) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Détail incident");
        alert.setHeaderText(incident.getCategory());
        alert.setContentText(
                "Statut : " + incident.getStatus() + "\n" +
                        "Priorité : " + "Normal" + "\n" +
                        "Description : " + incident.getDescription() + "\n" +
                        "Créé le : " + (incident.getCreatedAt() != null
                        ? incident.getCreatedAt().format(DATE_FMT) : "—") + "\n" +
                        "Synchronisé : " + (incident.isSynced() ? "Oui" : "Non")
        );
        alert.showAndWait();
    }

    //  Actions 
    @FXML
    public void onIncidentsClick() {
        System.out.println("[TODO] Ouvrir écran incidents");
    }

    @FXML
    public void onUsersClick() {
        System.out.println("[TODO] Ouvrir écran utilisateurs");
    }

    @FXML
    public void onStatisticsClick() {
        System.out.println("[TODO] Ouvrir écran statistiques");
    }

    @FXML
    public void onSettingsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/connectedneighbours/fxml/settings.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Paramètres — Connected Neighbours");
            stage.initOwner(btnSettings.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);

            Scene scene = new Scene(root, 720, 520);
            try {
                scene.getStylesheets().add(
                        getClass().getResource("/com/connectedneighbours/css/theme-light.css").toExternalForm()
                );
            } catch (Exception ignored) {
                // Thème optionnel
            }

            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();

            // Rafraîchit les infos après fermeture (au cas où l'URL API aurait changé).
            loadData();
        } catch (Exception e) {
            showError("Impossible d'ouvrir les paramètres : " + e.getMessage());
        }
    }

    @FXML
    public void onLogoutClick() {
        if (appContext == null) return;
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Se déconnecter ?",
                ButtonType.YES, ButtonType.NO
        );
        confirm.setTitle("Déconnexion");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) return;

        appContext.logout();
        Stage stage = (Stage) btnLogout.getScene().getWindow();
        Object mainApp = stage.getUserData();
        if (mainApp instanceof MainApp app) {
            app.backToLogin();
        } else {
            stage.close();
        }
    }

    @FXML
    public void onExportClick() {
        System.out.println("[TODO] Exporter les statistiques");
    }

    @FXML
    public void onNewIncidentClick() {
        // TODO : ouvrir une dialog de création d'incident
        System.out.println("[TODO] Créer un nouvel incident");
    }

    @FXML
    public void onSyncNowClick() {
        if (syncService != null) {
            syncNowButton.setDisable(true);
            syncService.syncNow();
        }
    }

    //  Mise à jour de la barre de sync
    private void updateSyncUI(SyncStatus status) {
        switch (status) {
            case OFFLINE -> {
                syncStatusLabel.setText("Hors-ligne");
                syncStatusDot.setFill(Color.GRAY);
                syncNowButton.setDisable(false);
            }
            case SYNCING -> {
                syncStatusLabel.setText("Synchronisation en cours...");
                syncStatusDot.setFill(Color.ORANGE);
                syncNowButton.setDisable(true);
            }
            case SUCCESS -> {
                syncStatusLabel.setText("Synchronisé");
                syncStatusDot.setFill(Color.GREEN);
                syncNowButton.setDisable(false);
                lastSyncLabel.setText("Dernière sync : " + LocalDateTime.now().format(DATE_FMT));
                loadData(); // rafraîchir les données après une sync réussie
            }
            case ERROR -> {
                syncStatusLabel.setText("Erreur de synchronisation");
                syncStatusDot.setFill(Color.RED);
                syncNowButton.setDisable(false);
            }
        }
    }

    //  Helpers couleurs alertes 
    private String alertColor(String type) {
        return switch (type != null ? type : "") {
            case "DANGER" -> "#e74c3c";
            case "WARNING" -> "#f39c12";
            default -> "#3498db";
        };
    }

    private String alertBgColor(String type) {
        return switch (type != null ? type : "") {
            case "DANGER" -> "#fdf0f0";
            case "WARNING" -> "#fefaf0";
            default -> "#f0f7fe";
        };
    }

    private void showError(String msg) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
