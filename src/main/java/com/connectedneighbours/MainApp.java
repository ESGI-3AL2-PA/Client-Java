package com.connectedneighbours;

import com.connectedneighbours.config.AuthConfig;
import com.connectedneighbours.config.SessionConfig;
import com.connectedneighbours.controller.DashboardController;
import com.connectedneighbours.controller.HeaderController;
import com.connectedneighbours.controller.IncidentController;
import com.connectedneighbours.controller.Page;
import com.connectedneighbours.i18n.I18nManager;
import com.connectedneighbours.model.User;
import com.connectedneighbours.plugin.PluginManager;
import com.connectedneighbours.repository.DatabaseManager;
import com.connectedneighbours.repository.DistrictRepository;
import com.connectedneighbours.service.SyncService;
import com.connectedneighbours.theme.ThemeManager;
import com.connectedneighbours.util.DatabaseUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.SQLException;

public class MainApp extends Application {

    private AppContext appContext;
    private SyncService syncService;
    private Stage primaryStage;
    private Page currentPage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.appContext = new AppContext();

        ThemeManager.reloadCustomThemes();

        PluginManager.init(appContext);
        PluginManager.loadAll();

        // Mode offline-first : si un dernier utilisateur ADMIN est mémorisé
        // ET que la base H2 locale contient des données --> skip le login SSO et on ouvre directement le dashboard.
        //
        // Le contrôle de rôle est indispensable ici : ce chemin ouvre le dashboard
        // sans login ni token, sur la seule foi d'une ligne en base locale. Sans
        // lui, un compte non-admin mémorisé une fois garderait un accès complet à
        // l'interface d'administration hors ligne, indéfiniment.
        User restored = SessionConfig.loadLastUser().orElse(null);
        if (restored != null && !AuthConfig.isAdminRole(restored.getRole())) {
            SessionConfig.clearLastUser();
            restored = null;
        }
        if (restored != null && hasLocalData()) {
            onAuthenticated(restored);
            showDashboard();
            return;
        }

        // Affiche immédiatement une fenêtre d'accueil (le navigateur va s'ouvrir pour le login).
        showWaiting(I18nManager.tr("mainapp.waiting.title.connecting"),
                I18nManager.tr("mainapp.waiting.message.connecting"));

        // Lance le login navigateur sur un thread d'arrière-plan.
        Task<User> login = new Task<>() {
            @Override
            protected User call() throws Exception {
                return appContext.getAuthService().loginViaBrowser();
            }
        };
        login.setOnSucceeded(e -> {
            User user = login.getValue();
            onAuthenticated(user);
            SessionConfig.saveLastUser(user);
            showDashboard();
        });
        login.setOnFailed(e -> {
            Throwable t = login.getException();
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            showWaiting(I18nManager.tr("mainapp.waiting.title.failed"),
                    I18nManager.tr("mainapp.waiting.message.failed", msg));
        });
        Thread t = new Thread(login, "sso-browser-login");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Installe l'utilisateur et son périmètre quartier. Les quartiers sont lus en
     * base locale, pas via l'api : ce chemin est aussi emprunté au démarrage
     * offline-first, où aucun appel réseau n'est possible.
     */
    private void onAuthenticated(User user) {
        appContext.setCurrentUser(user);
        appContext.initDistrictScope(new DistrictRepository().findAll());
    }

    private boolean hasLocalData() {
        String[] tables = {"INCIDENTS", "ALERTS", "USERS", "STATISTICS", "SYNC_LOG"};
        for (String table : tables) {
            try {
                if (DatabaseUtil.countRows(table, null) > 0) {
                    return true;
                }
            } catch (SQLException ignored) {
                // Table inexistante ou erreur lecture → on ignore et on
                // continue ; le fallback est le login navigateur.
            }
        }
        return false;
    }

    /**
     * Affiche une fenêtre simple avec un message
     */
    private void showWaiting(String title, String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Label label = new javafx.scene.control.Label(message);
            label.setWrapText(true);
            label.getStyleClass().add("waiting-label");
            javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(label);
            root.setPadding(new javafx.geometry.Insets(24));
            root.getStyleClass().add("app-bg");
            Scene scene = new Scene(root, 480, 200);
            ThemeManager.applyTheme(scene);
            primaryStage.setTitle(I18nManager.tr("mainapp.window.title.suffixed", title));
            primaryStage.setScene(scene);
            primaryStage.show();
        });
    }

    public void showDashboard() {
        Platform.runLater(() -> {
            try {
                ensureSyncService();

                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/connectedneighbours/fxml/dashboard.fxml")
                );
                loader.setResources(I18nManager.getBundle());
                loader.setControllerFactory(cls -> {
                    if (cls == DashboardController.class)
                        return new DashboardController(appContext, syncService);
                    if (cls == HeaderController.class)
                        return new HeaderController(appContext);
                    try {
                        return cls.getDeclaredConstructors()[0].newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                Parent root = loader.load();

                // Le listener de statut vient d'être attaché par
                // setupSync() (initialize() du contrôleur, appelé pendant
                // loader.load()) : on peut démarrer sans risquer de perdre
                // le tout premier statut du cycle immédiat.
                syncService.start();

                Scene scene = new Scene(root, 1280, 800);
                ThemeManager.applyTheme(scene);

                primaryStage.setTitle(I18nManager.tr("mainapp.window.title.dashboard"));
                primaryStage.setScene(scene);
                primaryStage.setUserData(this);
                primaryStage.show();

                currentPage = Page.DASHBOARD;
            } catch (Exception e) {
                throw new RuntimeException(I18nManager.tr("mainapp.loadError.dashboard"), e);
            }
        });
    }

    public void showIncidents() {
        Platform.runLater(() -> {
            try {
                ensureSyncService();

                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/connectedneighbours/fxml/incidents.fxml")
                );
                loader.setResources(I18nManager.getBundle());
                loader.setControllerFactory(cls -> {
                    if (cls == IncidentController.class)
                        return new IncidentController(appContext, syncService);
                    if (cls == HeaderController.class)
                        return new HeaderController(appContext);
                    try {
                        return cls.getDeclaredConstructors()[0].newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                Parent root = loader.load();

                // Cf. showDashboard() : le listener vient d'être attaché,
                // on démarre maintenant pour ne pas perdre le premier statut.
                syncService.start();

                Scene scene = new Scene(root, 1280, 800);
                ThemeManager.applyTheme(scene);

                primaryStage.setTitle(I18nManager.tr("mainapp.window.title.incidents"));
                primaryStage.setScene(scene);
                primaryStage.setUserData(this);
                primaryStage.show();

                currentPage = Page.INCIDENTS;
            } catch (Exception e) {
                throw new RuntimeException(I18nManager.tr("mainapp.loadError.incidents"), e);
            }
        });
    }

    /**
     * Reconstruit l'écran actuellement affiché (dashboard ou incidents) à
     * partir de son FXML, avec la langue courante — utilisé pour le
     * changement de langue "à chaud" depuis les Paramètres.
     */
    public void reloadCurrentScreen() {
        if (currentPage == Page.INCIDENTS) {
            showIncidents();
        } else {
            showDashboard();
        }
    }

    /**
     * Crée le SyncService s'il n'existe pas encore. Ne le démarre pas :
     * {@link SyncService#start()} lance un premier cycle sans délai, qui peut
     * s'exécuter (et échouer côté auth pour un utilisateur restauré hors
     * ligne) avant que l'écran n'ait eu le temps d'attacher son
     * statusListener via setupSync(). Ce premier statut serait alors perdu
     * (notifyStatus l'ignore silencieusement si personne n'écoute encore),
     * et avec lui le déclenchement du re-login — la synchro ne repart
     * qu'au tick suivant, 30s plus tard. Le démarrage est donc repoussé
     * après le chargement du FXML, une fois le listener en place.
     */
    private void ensureSyncService() {
        if (syncService == null) {
            syncService = new SyncService(appContext.getSyncApiClient());
        }
    }

    /**
     * Relance le login navigateur après expiration du token : le refresh silencieux
     * est souhaitable ici, l'opérateur n'a pas demandé à changer de compte.
     */
    public void backToLogin() {
        backToLogin(false);
    }

    /**
     * @param forceReauth {@code true} depuis le bouton Déconnexion. La session vit
     *                    dans le cookie du navigateur, que le client Java ne peut pas
     *                    effacer : sans {@code prompt=login}, l'authorize rend
     *                    immédiatement un code pour le même compte.
     */
    public void backToLogin(boolean forceReauth) {
        if (syncService != null) {
            syncService.stop();
            syncService = null;
        }
        appContext.logout();

        showWaiting(I18nManager.tr("mainapp.waiting.title.connecting"),
                I18nManager.tr("mainapp.waiting.message.connecting"));

        Task<User> login = new Task<>() {
            @Override
            protected User call() throws Exception {
                return appContext.getAuthService().loginViaBrowser(forceReauth);
            }
        };
        login.setOnSucceeded(e -> {
            User user = login.getValue();
            onAuthenticated(user);
            SessionConfig.saveLastUser(user);
            showDashboard();
        });
        login.setOnFailed(e -> {
            Throwable t = login.getException();
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            showWaiting(I18nManager.tr("mainapp.waiting.title.failed"),
                    I18nManager.tr("mainapp.waiting.message.failed", msg));
        });
        Thread t = new Thread(login, "sso-browser-login");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop() throws Exception {
        PluginManager.shutdownAll();
        if (syncService != null) syncService.stop();
        DatabaseManager.close();
    }
}
