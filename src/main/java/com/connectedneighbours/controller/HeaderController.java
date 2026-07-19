package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.model.User;
import com.connectedneighbours.plugin.PluginManager;
import com.connectedneighbours.theme.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
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
    private MenuButton btnPlugins;
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
        navButtons.put(Page.STATISTICS, btnStatistics);
        disableUsersButton();
        refreshCurrentUserLabel();
    }

    /**
     * L'écran utilisateurs n'existe pas encore. Le bouton restait cliquable et
     * ne faisait rien, ce qui se lit comme une panne — on le désactive et on
     * dit pourquoi plutôt que d'avaler le clic en silence.
     *
     * <p>{@code btnUsers} est volontairement absent de {@link #navButtons} :
     * {@link #setActivePage(Page)} y fait un {@code setDisable(active)} qui
     * réactiverait le bouton dès qu'on quitte la page.</p>
     */
    private void disableUsersButton() {
        if (btnUsers == null) return;
        btnUsers.setDisable(true);
        btnUsers.setTooltip(new Tooltip("Écran utilisateurs à venir — non disponible dans cette version."));
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
    public void onStatisticsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/connectedneighbours/fxml/statistics.fxml")
            );
            loader.setResources(I18nManager.getBundle());
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(I18nManager.tr("header.window.statistics.title"));
            stage.initOwner(btnStatistics.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);

            Scene scene = new Scene(root, 900, 650);
            ThemeManager.applyTheme(scene);

            stage.setScene(scene);
            stage.setResizable(true);
            stage.showAndWait();
        } catch (Exception e) {
            showError(I18nManager.tr("header.error.statistics", e.getMessage()));
        }
    }

    @FXML
    public void onSettingsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/connectedneighbours/fxml/settings.fxml")
            );
            loader.setResources(I18nManager.getBundle());
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(I18nManager.tr("header.window.settings.title"));
            stage.initOwner(btnSettings.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);

            Scene scene = new Scene(root, 720, 520);
            ThemeManager.applyTheme(scene);

            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            showError(I18nManager.tr("header.error.settings", e.getMessage()));
        }
    }

    @FXML
    public void onSocialAnalysisClick() {
        PluginManager.execute("SocialAnalysisPlugin", appContext);
    }

    @FXML
    public void onLocalCalendarClick() {
        PluginManager.execute("LocalCalendarPlugin", appContext);
    }

    @FXML
    public void onLogoutClick() {
        if (appContext == null) return;
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                I18nManager.tr("header.logout.confirm.message"),
                Buttons.YES, Buttons.NO
        );
        confirm.setTitle(I18nManager.tr("header.logout.confirm.title"));
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(Buttons.NO) != Buttons.YES) return;

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
        alert.setTitle(I18nManager.tr("common.error.title"));
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
