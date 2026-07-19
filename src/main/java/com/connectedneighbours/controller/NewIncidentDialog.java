package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.service.IncidentService;
import com.connectedneighbours.theme.ThemeManager;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Dialogue de création d'incident, partagé par l'écran Incidents et le
 * tableau de bord.
 *
 * <p>Les deux écrans affichent un bouton « + Nouvel incident » : celui du
 * tableau de bord n'était qu'un {@code TODO} qui écrivait sur la sortie
 * standard, donc deux boutons identiques faisaient deux choses différentes.
 * Le formulaire vit ici pour qu'il n'y ait qu'une implémentation.</p>
 */
final class NewIncidentDialog {

    private NewIncidentDialog() {
    }

    /**
     * Ouvre le formulaire et crée l'incident si l'utilisateur valide.
     *
     * @return l'incident créé, ou {@link Optional#empty()} si annulé ou en échec.
     */
    static Optional<Incident> show(Window owner, IncidentService incidentService, AppContext appContext) {
        Dialog<Incident> dialog = new Dialog<>();
        dialog.setTitle("Nouvel incident");
        dialog.setHeaderText("Créer un nouvel incident");
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);

        ButtonType createType = new ButtonType("Créer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, Buttons.CANCEL);

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

        dialog.setResultConverter(bt -> {
            if (bt != createType) return null;
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
        });

        // Applique le thème courant à la scène du dialog (popup séparée).
        dialog.setOnShown(e -> ThemeManager.applyTheme(dialog.getDialogPane().getScene()));

        return dialog.showAndWait().filter(incident -> incident != null);
    }

    private static void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
