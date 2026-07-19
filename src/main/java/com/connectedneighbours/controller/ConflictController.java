package com.connectedneighbours.controller;

import com.connectedneighbours.service.ConflictService;
import com.connectedneighbours.sync.Conflict;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Écran de résolution des conflits — l'unique surface de résolution (§6.5).
 *
 * <p>La liste ne contient que les conflits que <em>cette</em> installation a
 * levés. Pour chacun, la table du bas confronte champ par champ la version
 * éditée hors-ligne et la version serveur, et laisse choisir laquelle garder ;
 * ces choix forment la charge de la résolution {@code merged}.</p>
 *
 * <p>Rien n'est effacé localement au moment de résoudre : la ligne en attente
 * disparaîtra quand l'état résolu redescendra par le flux.</p>
 */
public class ConflictController {

    private static final String CHOICE_LOCAL = "Ma version";
    private static final String CHOICE_SERVER = "Serveur";

    @FXML
    private TableView<Conflict> conflictsTable;
    @FXML
    private TableColumn<Conflict, String> colEntity;
    @FXML
    private TableColumn<Conflict, String> colType;
    @FXML
    private TableColumn<Conflict, String> colRecord;
    @FXML
    private TableColumn<Conflict, String> colDetectedAt;

    @FXML
    private TableView<FieldRow> fieldsTable;
    @FXML
    private TableColumn<FieldRow, String> colField;
    @FXML
    private TableColumn<FieldRow, String> colLocalValue;
    @FXML
    private TableColumn<FieldRow, String> colServerValue;
    @FXML
    private TableColumn<FieldRow, String> colChoice;

    @FXML
    private Label emptyLabel;
    @FXML
    private Button keepLocalButton;
    @FXML
    private Button keepServerButton;
    @FXML
    private Button mergeButton;

    private ConflictService conflictService;

    public ConflictController() {
    }

    public ConflictController(ConflictService conflictService) {
        this.conflictService = conflictService;
    }

    private static String display(Object value) {
        return value != null ? String.valueOf(value) : "—";
    }

    @FXML
    public void initialize() {
        setupTables();
        loadConflicts();
    }

    private void setupTables() {
        colEntity.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getEntity()));
        colType.setCellValueFactory(cell -> new ReadOnlyStringWrapper(typeLabel(cell.getValue().getType())));
        colRecord.setCellValueFactory(cell -> new ReadOnlyStringWrapper(shorten(cell.getValue().getMongoId())));
        colDetectedAt.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().getDetectedAt()));

        conflictsTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, previous, selected) -> showFields(selected));

        colField.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().field));
        colLocalValue.setCellValueFactory(cell -> new ReadOnlyStringWrapper(display(cell.getValue().localValue)));
        colServerValue.setCellValueFactory(cell -> new ReadOnlyStringWrapper(display(cell.getValue().serverValue)));
        colChoice.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().choice));
        colChoice.setCellFactory(column -> choiceCell());

        setActionsDisabled(true);
    }

    /**
     * Cellule d'arbitrage : un champ dont les deux versions sont identiques n'a
     * rien à arbitrer, on n'y met donc pas de sélecteur.
     */
    private TableCell<FieldRow, String> choiceCell() {
        return new TableCell<>() {
            private final ComboBox<String> combo =
                    new ComboBox<>(FXCollections.observableArrayList(CHOICE_LOCAL, CHOICE_SERVER));

            {
                combo.setOnAction(event -> {
                    FieldRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && combo.getValue() != null) {
                        row.choice = combo.getValue();
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                FieldRow row = getTableRow() != null ? getTableRow().getItem() : null;
                if (empty || row == null || !row.differs()) {
                    setGraphic(null);
                    setText(empty || row == null ? null : "identique");
                    return;
                }
                setText(null);
                combo.setValue(row.choice);
                setGraphic(combo);
            }
        };
    }

    private void loadConflicts() {
        try {
            ObservableList<Conflict> conflicts =
                    FXCollections.observableArrayList(conflictService.findMine());
            conflictsTable.setItems(conflicts);
            fieldsTable.setItems(FXCollections.observableArrayList());
            setActionsDisabled(true);

            emptyLabel.setText(conflicts.isEmpty()
                    ? "Aucun conflit à résoudre."
                    : conflicts.size() + " conflit" + (conflicts.size() > 1 ? "s" : "") + " en attente.");
        } catch (Exception e) {
            showError("Impossible de charger les conflits : " + e.getMessage());
        }
    }

    private void showFields(Conflict conflict) {
        if (conflict == null) {
            fieldsTable.setItems(FXCollections.observableArrayList());
            setActionsDisabled(true);
            return;
        }

        Map<String, Object> local = conflict.getLocalData() != null ? conflict.getLocalData() : Map.of();
        Map<String, Object> server = conflict.getServerData() != null ? conflict.getServerData() : Map.of();

        Set<String> fields = new LinkedHashSet<>(local.keySet());
        fields.addAll(server.keySet());

        ObservableList<FieldRow> rows = FXCollections.observableArrayList();
        for (String field : fields) {
            rows.add(new FieldRow(field, local.get(field), server.get(field)));
        }
        fieldsTable.setItems(rows);
        setActionsDisabled(false);
    }

    @FXML
    public void onKeepLocalClick() {
        Conflict selected = selectedConflict();
        if (selected == null) {
            return;
        }
        resolve(() -> conflictService.resolveWithLocal(selected.getId()));
    }

    @FXML
    public void onKeepServerClick() {
        Conflict selected = selectedConflict();
        if (selected == null) {
            return;
        }
        resolve(() -> conflictService.resolveWithServer(selected.getId()));
    }

    @FXML
    public void onMergeClick() {
        Conflict selected = selectedConflict();
        if (selected == null) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        for (FieldRow row : fieldsTable.getItems()) {
            merged.put(row.field, CHOICE_SERVER.equals(row.choice) ? row.serverValue : row.localValue);
        }
        resolve(() -> conflictService.resolveWithMerge(selected.getId(), merged));
    }

    @FXML
    public void onRefreshClick() {
        loadConflicts();
    }

    @FXML
    public void onCloseClick() {
        ((Stage) conflictsTable.getScene().getWindow()).close();
    }

    private void resolve(ResolveAction action) {
        try {
            action.run();
            // La ligne en attente n'est pas touchée ici : le prochain pull
            // ramènera l'état résolu et c'est lui qui nettoiera H2 (§6.5).
            loadConflicts();
        } catch (Exception e) {
            showError("Résolution impossible : " + e.getMessage());
        }
    }

    private Conflict selectedConflict() {
        return conflictsTable.getSelectionModel().getSelectedItem();
    }

    private void setActionsDisabled(boolean disabled) {
        keepLocalButton.setDisable(disabled);
        keepServerButton.setDisable(disabled);
        mergeButton.setDisable(disabled);
    }

    private String typeLabel(String type) {
        return switch (type != null ? type : "") {
            case "update" -> "Modification concurrente";
            case "duplicate" -> "Doublon";
            default -> type;
        };
    }

    private String shorten(String id) {
        if (id == null) {
            return "—";
        }
        return id.length() > 8 ? id.substring(0, 8) + "…" : id;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FunctionalInterface
    private interface ResolveAction {
        void run() throws Exception;
    }

    /**
     * Une ligne de la confrontation champ à champ.
     */
    public static class FieldRow {

        private final String field;
        private final Object localValue;
        private final Object serverValue;
        private String choice;

        FieldRow(String field, Object localValue, Object serverValue) {
            this.field = field;
            this.localValue = localValue;
            this.serverValue = serverValue;
            // Par défaut, la version de l'opérateur : c'est celle qu'il vient
            // d'écrire, et il est là pour arbitrer.
            this.choice = CHOICE_LOCAL;
        }

        public String getField() {
            return field;
        }

        public String getChoice() {
            return choice;
        }

        boolean differs() {
            return !java.util.Objects.equals(localValue, serverValue);
        }
    }
}
