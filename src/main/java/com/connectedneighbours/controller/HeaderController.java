package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.model.User;
import com.connectedneighbours.theme.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.Map;

/**
 * Contrôleur du header de navigation mutualisé entre les écrans
 * (dashboard, incidents, etc.).
 * <p>
 * La page active est signalée par le contrôleur parent via
 * {@link #setActivePage(Page)} dans son {@code initialize()}.
 */
public class HeaderController {

    private static final String STYLE_ACTIVE = "nav-button-active";
    private static final String STYLE_INACTIVE = "nav-button";

    @FXML
    private Button btnDashboard;
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

    private AppContext appContext;

    /**
     * Bouton de navigation par page (SETTINGS n'a pas de bouton de nav direct).
     */
    private Map<Page, Button> navButtons;

    public HeaderController() {
    }

    public HeaderController(AppContext appContext) {
        this.appContext = appContext;
    }

    @FXML
    public void initialize() {
        navButtons = new EnumMap<>(Page.class);
        navButtons.put(Page.DASHBOARD, btnDashboard);
        navButtons.put(Page.INCIDENTS, btnIncidents);
        navButtons.put(Page.USERS, btnUsers);
        navButtons.put(Page.STATISTICS, btnStatistics);
        refreshCurrentUserLabel();
    }

    private void refreshCurrentUserLabel() {
        if (currentUserLabel == null) return;
        User u = appContext != null ? appContext.getCurrentUser() : null;
        currentUserLabel.setText(u == null ? "" : u.getEmail());
    }

    /**
     * Met en évidence le bouton de la page courante (style actif + désactivé)
     * et réinitialise les autres boutons de navigation au style inactif.
     */
    public void setActivePage(Page page) {
        if (navButtons == null) {
            // initialize() pas encore appelé (ne devrait pas arriver).
            return;
        }
        for (Map.Entry<Page, Button> e : navButtons.entrySet()) {
            Button b = e.getValue();
            if (b == null) continue;
            boolean active = (e.getKey() == page);
            b.getStyleClass().removeAll(STYLE_ACTIVE, STYLE_INACTIVE);
            b.getStyleClass().add(active ? STYLE_ACTIVE : STYLE_INACTIVE);
            b.setDisable(active);
        }
    }

    //  Navigation

    @FXML
    public void onDashboardClick() {
        navigate(btnDashboard, MainApp::showDashboard);
    }

    @FXML
    public void onIncidentsClick() {
        navigate(btnIncidents, MainApp::showIncidents);
    }

    @FXML
    public void onUsersClick() {
        System.out.println("[TODO] Ouvrir écran utilisateurs");
    }

    @FXML
    public void onStatisticsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/connectedneighbours/fxml/statistics.fxml")
            );
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Statistiques — Connected Neighbours");
            stage.initOwner(btnStatistics.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);

            Scene scene = new Scene(root, 900, 650);
            ThemeManager.applyTheme(scene);

            stage.setScene(scene);
            stage.setResizable(true);
            stage.showAndWait();
        } catch (Exception e) {
            showError("Impossible d'ouvrir la page statistique : " + e.getMessage());
        }
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
            ThemeManager.applyTheme(scene);

            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();
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

    //  Helpers

    private void navigate(Button source, java.util.function.Consumer<MainApp> action) {
        if (source == null) return;
        Stage stage = (Stage) source.getScene().getWindow();
        Object mainApp = stage.getUserData();
        if (mainApp instanceof MainApp app) {
            action.accept(app);
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
