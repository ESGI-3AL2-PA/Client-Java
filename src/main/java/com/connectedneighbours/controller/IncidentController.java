package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.User;
import com.connectedneighbours.service.IncidentService;
import com.connectedneighbours.service.SyncService;
import com.connectedneighbours.service.SyncStatus;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class IncidentController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Header 
    @FXML private Button btnDashboard;
    @FXML private Button btnIncidents;
    @FXML private Button btnUsers;
    @FXML private Button btnStatistics;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;
    @FXML private Label currentUserLabel;

    // Filtres 
    @FXML private ComboBox<String> filterStatus;
    @FXML private ComboBox<String> filterCategory;
    @FXML private Label countLabel;

    // Table 
    @FXML private TableView<Incident> incidentsTable;
    @FXML private TableColumn<Incident, String> colId;
    @FXML private TableColumn<Incident, String> colCategory;
    @FXML private TableColumn<Incident, String> colDescription;
    @FXML private TableColumn<Incident, String> colStatus;
    @FXML private TableColumn<Incident, String> colReporter;
    @FXML private TableColumn<Incident, String> colDistrict;
    @FXML private TableColumn<Incident, String> colAssignedTo;
    @FXML private TableColumn<Incident, LocalDateTime> colCreatedAt;
    @FXML private TableColumn<Incident, LocalDateTime> colUpdatedAt;
    @FXML private TableColumn<Incident, String> colSynced;

    // Barre de sync 
    @FXML private Circle syncStatusDot;
    @FXML private Label syncStatusLabel;
    @FXML private Label lastSyncLabel;
    @FXML private Button syncNowButton;

    // Services 
    private AppContext appContext;
    private SyncService syncService;
    private IncidentService incidentService;
    private boolean reloginRequested = false;

    /** Master list (non-filtrée) */
    private List<Incident> allIncidents;

    public IncidentController() {
    }

    public IncidentController(AppContext appContext, SyncService syncService) {
        this.appContext = appContext;
        this.syncService = syncService;
        this.incidentService = new IncidentService();
    }

    // Initialisation 
    @FXML
    public void initialize() {
        if (incidentService == null) {
            incidentService = new IncidentService();
        }
        setupTable();
        setupFilters();
        loadData();
        if (syncService != null) {
            syncService.setStatusListener(this::updateSyncUI);
        }
        refreshCurrentUserLabel();
    }

    private void refreshCurrentUserLabel() {
        if (currentUserLabel == null) return;
        User u = appContext != null ? appContext.getCurrentUser() : null;
        String txt = (u == null) ? "" : u.getEmail();
        currentUserLabel.setText(txt);
    }

    // Configuration de la table 
    private void setupTable() {
        // ID (tronqué aux 8 premiers caractères)
        colId.setCellValueFactory(cell -> {
            String id = cell.getValue().getId();
            return new ReadOnlyStringWrapper(
                    id != null && id.length() > 8 ? id.substring(0, 8) + "…" : id);
        });

        // Catégorie
        colCategory.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().getCategory()));

        // Description (tronquée à 50 caractères)
        colDescription.setCellValueFactory(cell -> {
            String desc = cell.getValue().getDescription();
            if (desc != null && desc.length() > 50) {
                desc = desc.substring(0, 50) + "…";
            }
            return new ReadOnlyStringWrapper(desc);
        });

        // Statut (texte)
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Reporter (tronqué)
        colReporter.setCellValueFactory(cell -> {
            String rid = cell.getValue().getReporterId();
            return new ReadOnlyStringWrapper(
                    rid != null && rid.length() > 8 ? rid.substring(0, 8) + "…" : rid);
        });

        // District (tronqué)
        colDistrict.setCellValueFactory(cell -> {
            String did = cell.getValue().getDistrictId();
            return new ReadOnlyStringWrapper(
                    did != null && did.length() > 8 ? did.substring(0, 8) + "…" : did);
        });

        // Assigned To
        colAssignedTo.setCellValueFactory(cell -> {
            String a = cell.getValue().getAssignedTo();
            return new ReadOnlyStringWrapper(
                    a != null && a.length() > 8 ? a.substring(0, 8) + "…" : a);
        });

        // Dates
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        colCreatedAt.setCellFactory(col -> dateCell());

        colUpdatedAt.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        colUpdatedAt.setCellFactory(col -> dateCell());

        // Synced
        colSynced.setCellValueFactory(cell ->
                new ReadOnlyStringWrapper(cell.getValue().isSynced() ? "✓" : "✗"));

        // Couleur du statut
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(statusLabel(item));
                setStyle(switch (item) {
                    case "open" -> "-fx-text-fill: #e74c3c; -fx-font-weight: bold;";
                    case "in_progress" -> "-fx-text-fill: #f39c12; -fx-font-weight: bold;";
                    case "resolved" -> "-fx-text-fill: #27ae60; -fx-font-weight: bold;";
                    case "closed" -> "-fx-text-fill: #95a5a6; -fx-font-weight: bold;";
                    default -> "";
                });
            }
        });

        // Couleur synced
        colSynced.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                setStyle("✓".equals(item)
                        ? "-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-alignment: CENTER;"
                        : "-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-alignment: CENTER;");
            }
        });

        // Double-clic → dialog d'édition
        incidentsTable.setRowFactory(tv -> {
            TableRow<Incident> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openEditDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private TableCell<Incident, LocalDateTime> dateCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(DATE_FMT));
                }
            }
        };
    }

    // Filtres 
    private void setupFilters() {
        // Statut
        filterStatus.setItems(FXCollections.observableArrayList(
                "Tous", "Ouvert", "En cours", "Résolu", "Fermé"
        ));
        filterStatus.setValue("Tous");
        filterStatus.setOnAction(e -> applyFilters());

        // Catégorie
        filterCategory.setItems(FXCollections.observableArrayList("Toutes"));
        filterCategory.setValue("Toutes");
        filterCategory.setOnAction(e -> applyFilters());
    }

    private void refreshCategoryFilter() {
        String current = filterCategory.getValue();
        List<String> categories = incidentService.getDistinctCategories();
        ObservableList<String> items = FXCollections.observableArrayList("Toutes");
        items.addAll(categories);
        filterCategory.setItems(items);
        // Restore selection if still valid
        if (current != null && items.contains(current)) {
            filterCategory.setValue(current);
        } else {
            filterCategory.setValue("Toutes");
        }
    }

    private void applyFilters() {
        if (allIncidents == null) return;

        String statusFilter = filterStatus.getValue();
        String categoryFilter = filterCategory.getValue();

        List<Incident> filtered = allIncidents.stream()
                .filter(i -> {
                    if (statusFilter == null || "Tous".equals(statusFilter)) return true;
                    String statusValue = statusFilterToValue(statusFilter);
                    return statusValue.equals(i.getStatus());
                })
                .filter(i -> {
                    if (categoryFilter == null || "Toutes".equals(categoryFilter)) return true;
                    return categoryFilter.equals(i.getCategory());
                })
                .sorted(Incident.BY_CREATED_AT_DESC)
                .collect(Collectors.toList());

        incidentsTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() + " incident" + (filtered.size() > 1 ? "s" : ""));
    }

    private String statusFilterToValue(String label) {
        return switch (label) {
            case "Ouvert" -> "open";
            case "En cours" -> "in_progress";
            case "Résolu" -> "resolved";
            case "Fermé" -> "closed";
            default -> "";
        };
    }

    private String statusLabel(String value) {
        return switch (value != null ? value : "") {
            case "open" -> "Ouvert";
            case "in_progress" -> "En cours";
            case "resolved" -> "Résolu";
            case "closed" -> "Fermé";
            default -> value;
        };
    }

    // Chargement des données 

    private void loadData() {
        allIncidents = incidentService.getAllIncidents();
        refreshCategoryFilter();
        applyFilters();
    }

    // Création d'incident 

    @FXML
    public void onNewIncidentClick() {
        Dialog<Incident> dialog = new Dialog<>();
        dialog.setTitle("Nouvel incident");
        dialog.setHeaderText("Créer un nouvel incident");
        dialog.initOwner(incidentsTable.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);

        // Boutons
        ButtonType createType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        // Formulaire
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 10, 24));

        TextField categoryField = new TextField();
        categoryField.setPromptText("Ex: Bruit, Voirie, Propreté…");
        categoryField.setPrefWidth(300);

        TextArea descriptionField = new TextArea();
        descriptionField.setPromptText("Description de l'incident…");
        descriptionField.setPrefRowCount(4);
        descriptionField.setWrapText(true);

        grid.add(new Label("Catégorie *"), 0, 0);
        grid.add(categoryField, 1, 0);
        grid.add(new Label("Description *"), 0, 1);
        grid.add(descriptionField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Validation : désactiver le bouton Créer si champs vides
        dialog.getDialogPane().lookupButton(createType).setDisable(true);
        Runnable validate = () -> {
            boolean valid = !categoryField.getText().isBlank()
                    && !descriptionField.getText().isBlank();
            dialog.getDialogPane().lookupButton(createType).setDisable(!valid);
        };
        categoryField.textProperty().addListener((obs, o, n) -> validate.run());
        descriptionField.textProperty().addListener((obs, o, n) -> validate.run());

        // Résultat
        dialog.setResultConverter(bt -> {
            if (bt == createType) {
                String reporterId = appContext != null && appContext.getCurrentUser() != null
                        ? appContext.getCurrentUser().getId() : "admin";
                try {
                    return incidentService.createIncident(
                            categoryField.getText().trim(),
                            descriptionField.getText().trim(),
                            reporterId
                    );
                } catch (Exception e) {
                    showError("Erreur de création : " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        Optional<Incident> result = dialog.showAndWait();
        if (result.isPresent() && result.get() != null) {
            loadData();
        }
    }

    //  Édition d'incident (dialog modale) 

    private void openEditDialog(Incident incident) {
        // Recharger depuis le repo pour avoir les données fraîches
        Incident fresh = incidentService.getIncidentById(incident.getId()).orElse(incident);

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Détail — Incident");
        dialog.setHeaderText("ID : " + fresh.getId());
        dialog.initOwner(incidentsTable.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);

        ButtonType saveType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        // Formulaire
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 10, 24));

        // Statut
        ComboBox<String> statusBox = new ComboBox<>(FXCollections.observableArrayList(
                "open", "in_progress", "resolved", "closed"
        ));
        statusBox.setValue(fresh.getStatus());
        statusBox.setCellFactory(lv -> statusComboCell());
        statusBox.setButtonCell(statusComboCell());

        // Catégorie
        TextField categoryField = new TextField(fresh.getCategory());
        categoryField.setPrefWidth(300);

        // Description
        TextArea descriptionField = new TextArea(fresh.getDescription());
        descriptionField.setPrefRowCount(4);
        descriptionField.setWrapText(true);

        // Assigned To
        TextField assignedToField = new TextField(
                fresh.getAssignedTo() != null ? fresh.getAssignedTo() : "");
        assignedToField.setPromptText("ID de l'utilisateur assigné");

        // Infos lecture seule
        Label reporterLabel = new Label(fresh.getReporterId() != null ? fresh.getReporterId() : "—");
        Label districtLabel = new Label(fresh.getDistrictId() != null ? fresh.getDistrictId() : "—");
        Label createdLabel = new Label(
                fresh.getCreatedAt() != null ? fresh.getCreatedAt().format(DATE_FMT) : "—");
        Label updatedLabel = new Label(
                fresh.getUpdatedAt() != null ? fresh.getUpdatedAt().format(DATE_FMT) : "—");
        Label syncedLabel = new Label(fresh.isSynced() ? "✓ Synchronisé" : "✗ Non synchronisé");
        syncedLabel.setStyle(fresh.isSynced()
                ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                : "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

        int row = 0;
        grid.add(new Label("Statut"), 0, row);
        grid.add(statusBox, 1, row++);
        grid.add(new Label("Catégorie"), 0, row);
        grid.add(categoryField, 1, row++);
        grid.add(new Label("Description"), 0, row);
        grid.add(descriptionField, 1, row++);
        grid.add(new Label("Assigné à"), 0, row);
        grid.add(assignedToField, 1, row++);

        grid.add(new Separator(), 0, row++);
        grid.add(new Separator(), 1, row - 1);

        grid.add(new Label("Reporter"), 0, row);
        grid.add(reporterLabel, 1, row++);
        grid.add(new Label("District"), 0, row);
        grid.add(districtLabel, 1, row++);
        grid.add(new Label("Créé le"), 0, row);
        grid.add(createdLabel, 1, row++);
        grid.add(new Label("Modifié le"), 0, row);
        grid.add(updatedLabel, 1, row++);
        grid.add(new Label("Synchronisation"), 0, row);
        grid.add(syncedLabel, 1, row);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt == saveType) {
                fresh.setStatus(statusBox.getValue());
                fresh.setCategory(categoryField.getText().trim());
                fresh.setDescription(descriptionField.getText().trim());
                String assignee = assignedToField.getText().trim();
                fresh.setAssignedTo(assignee.isEmpty() ? null : assignee);
                try {
                    incidentService.updateIncident(fresh);
                    return true;
                } catch (Exception e) {
                    showError("Erreur de sauvegarde : " + e.getMessage());
                    return false;
                }
            }
            return false;
        });

        Optional<Boolean> result = dialog.showAndWait();
        if (result.isPresent() && result.get()) {
            loadData();
        }
    }

    private ListCell<String> statusComboCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(statusLabel(item));
                    setStyle(switch (item) {
                        case "open" -> "-fx-text-fill: #e74c3c;";
                        case "in_progress" -> "-fx-text-fill: #f39c12;";
                        case "resolved" -> "-fx-text-fill: #27ae60;";
                        case "closed" -> "-fx-text-fill: #95a5a6;";
                        default -> "";
                    });
                }
            }
        };
    }

    // Navigation 

    @FXML
    public void onDashboardClick() {
        Stage stage = (Stage) btnDashboard.getScene().getWindow();
        Object mainApp = stage.getUserData();
        if (mainApp instanceof MainApp app) {
            app.showDashboard();
        }
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

            loadData();
        } catch (Exception e) {
            showError("Impossible d'ouvrir les paramètres : " + e.getMessage());
        }
    }

    @FXML
    public void onLogoutClick() {
        if (appContext == null) return;
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
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
    public void onSyncNowClick() {
        if (syncService != null) {
            syncNowButton.setDisable(true);
            syncService.syncNow();
        }
    }

    //  Sync UI (même pattern que DashboardController)

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
                loadData();
            }
            case ERROR -> {
                syncStatusLabel.setText("Erreur de synchronisation");
                syncStatusDot.setFill(Color.RED);
                syncNowButton.setDisable(false);
            }
            case AUTH_REQUIRED -> {
                syncStatusLabel.setText("Reconnexion requise");
                syncStatusDot.setFill(Color.ORANGE);
                syncNowButton.setDisable(true);
                if (!reloginRequested) {
                    reloginRequested = true;
                    Stage stage = (Stage) btnLogout.getScene().getWindow();
                    Object mainApp = stage.getUserData();
                    if (mainApp instanceof MainApp app) {
                        app.backToLogin();
                    }
                }
            }
        }
    }

    //  Helpers

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
