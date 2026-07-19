package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.model.Alert;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.AlertRepository;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.StatisticRepository;
import com.connectedneighbours.service.IncidentService;
import com.connectedneighbours.service.SyncService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DashboardController extends BaseController {

    /** Fenêtre de la carte « Résolus (30j) », conforme à son libellé. */
    private static final int RESOLVED_WINDOW_DAYS = 30;

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
    private TableColumn<Incident, LocalDateTime> colDate;
    //  Alertes
    @FXML
    private VBox alertsContainer;
    //  Repositories
    private IncidentRepository incidentRepo;
    private AlertRepository alertRepo;
    private IncidentService incidentService;

    public DashboardController() {
    }

    public DashboardController(AppContext appContext, SyncService syncService) {
        super(appContext, syncService);
        this.incidentRepo = new IncidentRepository();
        this.alertRepo = new AlertRepository();
        this.incidentService = new IncidentService(this.incidentRepo);
    }

    @FXML
    public void initialize() {
        if (incidentRepo == null) {
            incidentRepo = new IncidentRepository();
        }
        if (alertRepo == null) {
            alertRepo = new AlertRepository();
        }
        if (incidentService == null) {
            incidentService = new IncidentService(incidentRepo);
        }
        setupTable();
        loadData();
        setupSync();
        setupDistrictScope();
        setupHeader(Page.DASHBOARD);
    }

    @Override
    protected void onSyncSuccess() {
        loadData();
    }

    @Override
    protected void onDistrictChanged() {
        loadData();
    }

    //  Config du TableView
    private void setupTable() {
        colCategory.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(cellData.getValue().getCategory()));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Colonne date avec formatage
        colDate.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
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
                    case "open" -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case "in_progress" -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                    case "resolved" -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
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
            // Une seule lecture, filtrée sur le quartier consulté : les compteurs sont
            // dérivés de cette liste plutôt que de COUNT() séparés, sinon les cartes et
            // le tableau « incidents récents » peuvent se contredire.
            List<Incident> incidents = incidentService.getIncidentsByDistrict(
                    appContext != null ? appContext.getActiveDistrictId() : null);

            int open = countByStatus(incidents, Incident.Status.OPEN);
            int inProgress = countByStatus(incidents, Incident.Status.IN_PROGRESS);
            int resolved = countResolvedSince(incidents, LocalDateTime.now().minusDays(RESOLVED_WINDOW_DAYS));
            int unsynced = (int) incidents.stream().filter(i -> !i.isSynced()).count();

            statOpenIncidents.setText(String.valueOf(open));
            statInProgress.setText(String.valueOf(inProgress));
            statResolved.setText(String.valueOf(resolved));
            statUnsynced.setText(String.valueOf(unsynced));

            // Tendances (comparaison simple)
            statOpenTrend.setText(open > 0
                    ? I18nManager.tr("dashboard.trend.openNeedsAttention")
                    : I18nManager.tr("dashboard.trend.noIncident"));
            statResolvedTrend.setText(I18nManager.tr("dashboard.trend.resolved30d"));

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
            showError(I18nManager.tr("dashboard.loadError", e.getMessage()));
        }
    }

    static int countByStatus(List<Incident> incidents, Incident.Status status) {
        return (int) incidents.stream()
                .filter(i -> status.getValue().equals(i.getStatus()))
                .count();
    }

    /**
     * Incidents résolus depuis {@code since}.
     * <p>
     * {@code updatedAt} sert d'approximation de la date de résolution : le modèle
     * ne porte pas de {@code resolvedAt}, et la dernière écriture d'un incident
     * résolu est sa résolution. Conséquence assumée : rouvrir puis re-résoudre, ou
     * simplement éditer un vieil incident résolu, le ramène dans la fenêtre. Une
     * mesure exacte demanderait un champ dédié, donc une migration et un changement
     * serveur.
     */
    static int countResolvedSince(List<Incident> incidents, LocalDateTime since) {
        return (int) incidents.stream()
                .filter(i -> Incident.Status.RESOLVED.getValue().equals(i.getStatus()))
                .filter(i -> i.getUpdatedAt() != null && i.getUpdatedAt().isAfter(since))
                .count();
    }

    //  Chargement des alertes
    private void loadAlerts() throws SQLException {
        alertsContainer.getChildren().clear();

        List<Alert> alerts = alertRepo.findRecent(5);

        if (alerts.isEmpty()) {
            Label empty = new Label(I18nManager.tr("dashboard.alerts.empty"));
            empty.getStyleClass().add("text-faint");
            empty.setStyle("-fx-font-size: 12px;");
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
        javafx.scene.control.Alert alert =
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(I18nManager.tr("dashboard.incidentDialog.title"));
        alert.setHeaderText(incident.getCategory());
        alert.setContentText(I18nManager.tr("dashboard.incidentDialog.content",
                incident.getStatus(),
                I18nManager.tr("dashboard.incidentDialog.priorityDefault"),
                incident.getDescription(),
                incident.getCreatedAt() != null ? incident.getCreatedAt().format(DATE_FMT) : "—",
                incident.isSynced() ? I18nManager.tr("common.value.yes") : I18nManager.tr("common.value.no")
        ));
        alert.showAndWait();
    }

    //  Actions

    @FXML
    public void onIncidentsClick() {
        Stage stage = (Stage) syncNowButton.getScene().getWindow();
        Object mainApp = stage.getUserData();
        if (mainApp instanceof MainApp app) {
            app.showIncidents();
        }
    }

    @FXML
    public void onExportClick() {
        // Convention volontairement différente de getIncidentsByDistrict(null), qui
        // rend tout : ici null cible la série districtId IS NULL, que
        // StatisticsService.recompute() écrit justement comme l'agrégat tous
        // quartiers. Les deux sont corrects pour leurs données — ne pas « unifier ».
        List<Statistic> stats = new StatisticRepository()
                .findByDistrictId(appContext != null ? appContext.getActiveDistrictId() : null);

        if(stats.isEmpty()) {
            showError(I18nManager.tr("dashboard.export.empty"));
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(I18nManager.tr("dashboard.export.chooser.title"));
        fileChooser.setInitialFileName("Statistiques_" + LocalDate.now() + ".json");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(I18nManager.tr("dashboard.export.chooser.filter"), "*.json")
        );

        File file = fileChooser.showSaveDialog(syncNowButton.getScene().getWindow());
        if(file == null) {
            return;
        }

        try {
            JacksonConfig.get().writerWithDefaultPrettyPrinter().writeValue(file, stats);

            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION,
                    I18nManager.tr("dashboard.export.success.message", file.getAbsolutePath()),
                    ButtonType.OK
            );
            alert.setTitle(I18nManager.tr("dashboard.export.success.title"));
            alert.setHeaderText(null);
            alert.showAndWait();

        } catch (IOException e) {
            showError(I18nManager.tr("dashboard.export.error", e.getMessage()));
        }


    }

    @FXML
    public void onNewIncidentClick() {
        NewIncidentDialog
                .show(incidentsTable.getScene().getWindow(), incidentService, appContext)
                .ifPresent(created -> loadData());
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
}
