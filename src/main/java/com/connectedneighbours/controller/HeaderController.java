package com.connectedneighbours.controller;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.MainApp;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.model.District;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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

    private static District allDistricts() {
        District d = new District();
        d.setName(I18nManager.tr("header.district.all"));
        return d;
    }

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
    @FXML
    private ComboBox<District> districtSelector;
    @FXML
    private Label districtBadge;

    private AppContext appContext;

    /**
     * Sentinelle « aucun filtre » du sélecteur. Un id {@code null} la distingue
     * d'un vrai quartier et correspond au périmètre non restreint.
     * <p>
     * Par instance et non statique : son libellé est traduit, et un champ statique
     * figerait la langue au chargement de la classe.
     */
    private final District allDistricts = allDistricts();

    /** Vrai pendant la reconstruction des items, pour ignorer la sélection transitoire. */
    private boolean repopulating;
    /** Converter + listener installés : une seule fois, quel que soit le nombre de rejeux. */
    private boolean districtSelectorReady;

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
        setupDistrictScope();
    }

    /**
     * Affiche le périmètre quartier : liste déroulante pour un superAdmin, badge
     * en lecture seule pour un admin — même répartition que l'AdminLayout du front
     * web. Si aucun quartier n'est résolu, on n'affiche rien plutôt qu'un contrôle
     * vide qui laisserait croire à une panne.
     */
    void setupDistrictScope() {
        if (appContext == null || districtSelector == null || districtBadge == null) return;

        if (appContext.canSwitchDistrict()) {
            List<District> districts = appContext.getAvailableDistricts();
            // Base locale encore vide (première connexion) : rien à proposer pour
            // l'instant. La première sync rappellera cette méthode.
            if (districts.isEmpty()) {
                hide(districtSelector);
                return;
            }

            // Drapeau explicite : ComboBox fournit un converter par défaut non nul,
            // donc tester getConverter() ne dirait jamais « pas encore installé ».
            if (!districtSelectorReady) {
                districtSelectorReady = true;
                districtSelector.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(District d) {
                        return d == null ? "" : d.getName();
                    }

                    @Override
                    public District fromString(String s) {
                        return null;
                    }
                });
                // Enregistré une seule fois : cette méthode est rejouée après chaque
                // sync, et ré-abonner à chaque passage empilerait les listeners.
                districtSelector.getSelectionModel().selectedItemProperty().addListener((obs, old, picked) -> {
                    if (picked != null && !repopulating) {
                        appContext.setActiveDistrictId(picked == allDistricts ? null : picked.getId());
                    }
                });
            }

            // Entrée « tous quartiers » en tête : la base d'un superAdmin contient
            // aussi des enregistrements sans quartier ou rattachés à un quartier
            // absent localement. Sans cette échappatoire ils ne seraient atteignables
            // depuis aucune sélection, donc invisibles.
            List<District> items = new ArrayList<>();
            items.add(allDistricts);
            items.addAll(districts);

            District active = districts.stream()
                    .filter(d -> d.getId() != null && d.getId().equals(appContext.getActiveDistrictId()))
                    .findFirst()
                    .orElse(allDistricts);

            // setAll() vide la sélection avant de la rétablir : sans ce garde, le
            // passage par « aucune sélection » écraserait le quartier actif.
            repopulating = true;
            try {
                districtSelector.getItems().setAll(items);
                districtSelector.getSelectionModel().select(active);
            } finally {
                repopulating = false;
            }
            show(districtSelector);
            return;
        }

        appContext.getActiveDistrictName().ifPresentOrElse(name -> {
            districtBadge.setText(name);
            show(districtBadge);
        }, () -> hide(districtBadge));
    }

    private static void hide(javafx.scene.Node node) {
        node.setVisible(false);
        node.setManaged(false);
    }

    private static void show(javafx.scene.Node node) {
        node.setVisible(true);
        node.setManaged(true);
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
            // Les statistiques sont historisées par quartier : l'écran doit lire la
            // série du quartier consulté, pas l'agrégat tous quartiers confondus.
            String districtId = appContext != null ? appContext.getActiveDistrictId() : null;
            loader.setControllerFactory(cls -> {
                if (cls == StatisticsController.class) {
                    return new StatisticsController(districtId);
                }
                try {
                    return cls.getDeclaredConstructors()[0].newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
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
            // Déconnexion explicite : on exige une ré-authentification, sinon le
            // cookie encore valide du navigateur reconnecte le même compte.
            app.backToLogin(true);
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
