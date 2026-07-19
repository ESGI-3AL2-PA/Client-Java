package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.model.District;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.DistrictRepository;
import com.connectedneighbours.repository.UserRepository;
import com.connectedneighbours.service.IncidentService;
import com.connectedneighbours.service.SyncService;
import com.connectedneighbours.theme.ThemeManager;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class IncidentController extends BaseController {

    /**
     * Formateur pour les dates des incidents (cellules, dialogues).
     */
    private static final DateTimeFormatter INCIDENT_DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Filtres
    @FXML
    private ComboBox<String> filterStatus;
    @FXML
    private ComboBox<String> filterCategory;
    @FXML
    private Label countLabel;

    // Table 
    @FXML
    private TableView<Incident> incidentsTable;
    @FXML
    private TableColumn<Incident, String> colId;
    @FXML
    private TableColumn<Incident, String> colCategory;
    @FXML
    private TableColumn<Incident, String> colDescription;
    @FXML
    private TableColumn<Incident, String> colStatus;
    @FXML
    private TableColumn<Incident, String> colReporter;
    @FXML
    private TableColumn<Incident, String> colDistrict;
    @FXML
    private TableColumn<Incident, String> colAssignedTo;
    @FXML
    private TableColumn<Incident, LocalDateTime> colCreatedAt;
    @FXML
    private TableColumn<Incident, LocalDateTime> colUpdatedAt;
    @FXML
    private TableColumn<Incident, String> colSynced;

    // Services
    private IncidentService incidentService;
    private UserRepository userRepo;
    private DistrictRepository districtRepo;

    /**
     * Master list (non-filtrée)
     */
    private List<Incident> allIncidents;

    /**
     * Caches pour résolution id → nom (rechargés à chaque loadData).
     */
    private Map<String, User> usersMap = new HashMap<>();
    private Map<String, District> districtsMap = new HashMap<>();

    public IncidentController() {
    }

    public IncidentController(AppContext appContext, SyncService syncService) {
        super(appContext, syncService);
        this.incidentService = new IncidentService();
        this.userRepo = new UserRepository();
        this.districtRepo = new DistrictRepository();
    }

    // Initialisation
    @FXML
    public void initialize() {
        if (incidentService == null) {
            incidentService = new IncidentService();
        }
        if (userRepo == null) {
            userRepo = new UserRepository();
        }
        if (districtRepo == null) {
            districtRepo = new DistrictRepository();
        }
        setupTable();
        setupFilters();
        loadData();
        setupSync();
        setupDistrictScope();
        setupHeader(Page.INCIDENTS);
    }

    @Override
    protected void onSyncSuccess() {
        loadData();
    }

    @Override
    protected void onDistrictChanged() {
        loadData();
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

        // Reporter (Prénom Nom)
        colReporter.setCellValueFactory(cell -> {
            String rid = cell.getValue().getReporterId();
            return new ReadOnlyStringWrapper(resolveUserName(rid));
        });

        // District (Nom)
        colDistrict.setCellValueFactory(cell -> {
            String did = cell.getValue().getDistrictId();
            return new ReadOnlyStringWrapper(resolveDistrictName(did));
        });

        // Assigned To (Prénom Nom)
        colAssignedTo.setCellValueFactory(cell -> {
            String a = cell.getValue().getAssignedTo();
            if (a == null || a.isBlank()) return new ReadOnlyStringWrapper("");
            return new ReadOnlyStringWrapper(resolveUserName(a));
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
                    setText(item.format(INCIDENT_DATE_FMT));
                }
            }
        };
    }

    // Filtres

    /** Sentinelle technique pour "Toutes les catégories" (catégorie = donnée utilisateur, non traduite). */
    private static final String CATEGORY_ALL = "";

    private void setupFilters() {
        // Statut — valeurs techniques stables (codes API), libellé traduit via une cell factory
        // (voir statusLabel) plutôt que stocker le libellé traduit comme valeur de la ComboBox :
        // ça évite de casser le filtrage quand la langue change.
        filterStatus.setItems(FXCollections.observableArrayList(
                "all", "open", "in_progress", "resolved", "closed"
        ));
        filterStatus.setCellFactory(lv -> statusFilterCell());
        filterStatus.setButtonCell(statusFilterCell());
        filterStatus.setValue("all");
        filterStatus.setOnAction(e -> applyFilters());

        // Catégorie
        filterCategory.setCellFactory(lv -> categoryFilterCell());
        filterCategory.setButtonCell(categoryFilterCell());
        filterCategory.setItems(FXCollections.observableArrayList(CATEGORY_ALL));
        filterCategory.setValue(CATEGORY_ALL);
        filterCategory.setOnAction(e -> applyFilters());
    }

    private ListCell<String> statusFilterCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : statusLabel(item));
            }
        };
    }

    private ListCell<String> categoryFilterCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(CATEGORY_ALL.equals(item) ? I18nManager.tr("incidents.category.all") : item);
                }
            }
        };
    }

    private void refreshCategoryFilter() {
        String current = filterCategory.getValue();
        List<String> categories = incidentService.getDistinctCategories();
        ObservableList<String> items = FXCollections.observableArrayList(CATEGORY_ALL);
        items.addAll(categories);
        filterCategory.setItems(items);
        // Restore selection if still valid
        if (current != null && items.contains(current)) {
            filterCategory.setValue(current);
        } else {
            filterCategory.setValue(CATEGORY_ALL);
        }
    }

    private void applyFilters() {
        if (allIncidents == null) return;

        String statusFilter = filterStatus.getValue();
        String categoryFilter = filterCategory.getValue();

        List<Incident> filtered = allIncidents.stream()
                .filter(i -> {
                    if (statusFilter == null || "all".equals(statusFilter)) return true;
                    return statusFilter.equals(i.getStatus());
                })
                .filter(i -> {
                    if (categoryFilter == null || CATEGORY_ALL.equals(categoryFilter)) return true;
                    return categoryFilter.equals(i.getCategory());
                })
                .sorted(Incident.BY_CREATED_AT_DESC)
                .collect(Collectors.toList());

        incidentsTable.setItems(FXCollections.observableArrayList(filtered));
        countLabel.setText(filtered.size() == 1
                ? I18nManager.tr("incidents.count.singular", filtered.size())
                : I18nManager.tr("incidents.count.plural", filtered.size()));
    }

    private String statusLabel(String value) {
        return switch (value != null ? value : "") {
            case "all" -> I18nManager.tr("incidents.status.all");
            case "open" -> I18nManager.tr("incidents.status.open");
            case "in_progress" -> I18nManager.tr("incidents.status.inProgress");
            case "resolved" -> I18nManager.tr("incidents.status.resolved");
            case "closed" -> I18nManager.tr("incidents.status.closed");
            default -> value;
        };
    }

    // Chargement des données 

    private void loadData() {
        // Recharger les caches de résolution
        usersMap = new HashMap<>();
        for (User u : userRepo.findAll()) {
            usersMap.put(u.getId(), u);
        }
        districtsMap = new HashMap<>();
        for (District d : districtRepo.findAll()) {
            districtsMap.put(d.getId(), d);
        }

        allIncidents = incidentService.getIncidentsByDistrict(
                appContext != null ? appContext.getActiveDistrictId() : null);
        refreshCategoryFilter();
        applyFilters();
    }

    // Création d'incident 

    @FXML
    public void onNewIncidentClick() {
        NewIncidentDialog
                .show(incidentsTable.getScene().getWindow(), incidentService, appContext)
                .ifPresent(created -> loadData());
    }

    //  Édition d'incident (dialog modale) 

    private void openEditDialog(Incident incident) {
        // Recharger depuis le repo pour avoir les données fraîches
        Incident fresh = incidentService.getIncidentById(incident.getId()).orElse(incident);

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(I18nManager.tr("incidents.editDialog.title"));
        dialog.setHeaderText(I18nManager.tr("incidents.editDialog.header", fresh.getId()));
        dialog.initOwner(incidentsTable.getScene().getWindow());
        dialog.initModality(Modality.WINDOW_MODAL);

        ButtonType saveType = new ButtonType(I18nManager.tr("common.action.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, Buttons.CANCEL);

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
        List<User> districtAdmins = userRepo.findAdminsByDistrictId(
                fresh.getDistrictId() != null ? fresh.getDistrictId() : "");

        // Sentinelle pour "— Non assigné —"
        User emptyUser = new User();
        emptyUser.setId(null);
        emptyUser.setFirstName(I18nManager.tr("incidents.editDialog.assignedTo.none"));
        emptyUser.setLastName("");

        ObservableList<User> assignOptions = FXCollections.observableArrayList();
        assignOptions.add(emptyUser);
        assignOptions.addAll(districtAdmins);

        ComboBox<User> assignedToBox = new ComboBox<>(assignOptions);
        assignedToBox.setPrefWidth(300);

        // Convertisseur d'affichage Prénom Nom
        StringConverter<User> userConverter = new StringConverter<>() {
            @Override
            public String toString(User user) {
                if (user == null) return "";
                if (user.getId() == null) return I18nManager.tr("incidents.editDialog.assignedTo.none");
                String fn = user.getFirstName() != null ? user.getFirstName() : "";
                String ln = user.getLastName() != null ? user.getLastName() : "";
                return (fn + " " + ln).trim();
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        };
        assignedToBox.setConverter(userConverter);
        assignedToBox.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : userConverter.toString(item));
            }
        });
        assignedToBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : userConverter.toString(item));
            }
        });

        // Pré-sélection
        if (fresh.getAssignedTo() != null && !fresh.getAssignedTo().isBlank()) {
            assignOptions.stream()
                    .filter(u -> fresh.getAssignedTo().equals(u.getId()))
                    .findFirst()
                    .ifPresentOrElse(assignedToBox::setValue, () -> assignedToBox.setValue(emptyUser));
        } else {
            assignedToBox.setValue(emptyUser);
        }

        // Infos lecture seule
        String reporterDisplay = resolveUserName(fresh.getReporterId());
        String districtDisplay = resolveDistrictName(fresh.getDistrictId());
        Label reporterLabel = new Label(reporterDisplay);
        Label districtLabel = new Label(districtDisplay);
        Label createdLabel = new Label(
                fresh.getCreatedAt() != null ? fresh.getCreatedAt().format(INCIDENT_DATE_FMT) : "—");
        Label updatedLabel = new Label(
                fresh.getUpdatedAt() != null ? fresh.getUpdatedAt().format(INCIDENT_DATE_FMT) : "—");
        Label syncedLabel = new Label(fresh.isSynced()
                ? I18nManager.tr("incidents.editDialog.synced.yes")
                : I18nManager.tr("incidents.editDialog.synced.no"));
        syncedLabel.setStyle(fresh.isSynced()
                ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                : "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

        int row = 0;
        grid.add(new Label(I18nManager.tr("common.field.status")), 0, row);
        grid.add(statusBox, 1, row++);
        grid.add(new Label(I18nManager.tr("common.field.category")), 0, row);
        grid.add(categoryField, 1, row++);
        grid.add(new Label(I18nManager.tr("common.field.description")), 0, row);
        grid.add(descriptionField, 1, row++);
        grid.add(new Label(I18nManager.tr("common.field.assignedTo")), 0, row);
        grid.add(assignedToBox, 1, row++);

        grid.add(new Separator(), 0, row++);
        grid.add(new Separator(), 1, row - 1);

        grid.add(new Label(I18nManager.tr("common.field.reporter")), 0, row);
        grid.add(reporterLabel, 1, row++);
        grid.add(new Label(I18nManager.tr("common.field.district")), 0, row);
        grid.add(districtLabel, 1, row++);
        grid.add(new Label(I18nManager.tr("common.field.createdAt")), 0, row);
        grid.add(createdLabel, 1, row++);
        grid.add(new Label(I18nManager.tr("common.field.updatedAt")), 0, row);
        grid.add(updatedLabel, 1, row++);
        grid.add(new Label(I18nManager.tr("incidents.editDialog.field.synchronization")), 0, row);
        grid.add(syncedLabel, 1, row);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(bt -> {
            if (bt == saveType) {
                fresh.setStatus(statusBox.getValue());
                fresh.setCategory(categoryField.getText().trim());
                fresh.setDescription(descriptionField.getText().trim());
                User selectedUser = assignedToBox.getValue();
                fresh.setAssignedTo(selectedUser != null && selectedUser.getId() != null
                        ? selectedUser.getId() : null);
                try {
                    incidentService.updateIncident(fresh);
                    return true;
                } catch (Exception e) {
                    showError(I18nManager.tr("incidents.editDialog.saveError", e.getMessage()));
                    return false;
                }
            }
            return false;
        });

        // Applique le thème courant à la scène du dialog (popup séparée).
        dialog.setOnShown(e -> ThemeManager.applyTheme(dialog.getDialogPane().getScene()));

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

    // Résolution id → nom

    private String resolveUserName(String userId) {
        if (userId == null || userId.isBlank()) return "—";
        User u = usersMap.get(userId);
        if (u == null) return I18nManager.tr("common.value.unknown");
        String fn = u.getFirstName() != null ? u.getFirstName() : "";
        String ln = u.getLastName() != null ? u.getLastName() : "";
        String name = (fn + " " + ln).trim();
        return name.isEmpty() ? I18nManager.tr("common.value.unknown") : name;
    }

    private String resolveDistrictName(String districtId) {
        if (districtId == null || districtId.isBlank()) return "—";
        District d = districtsMap.get(districtId);
        if (d == null) return I18nManager.tr("common.value.unknown");
        return d.getName() != null ? d.getName() : I18nManager.tr("common.value.unknown");
    }
}
