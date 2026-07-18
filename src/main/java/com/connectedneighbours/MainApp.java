package com.connectedneighbours;

import com.connectedneighbours.config.AuthConfig;
import com.connectedneighbours.config.SessionConfig;
import com.connectedneighbours.controller.DashboardController;
import com.connectedneighbours.controller.HeaderController;
import com.connectedneighbours.controller.IncidentController;
import com.connectedneighbours.model.User;
import com.connectedneighbours.plugin.PluginManager;
import com.connectedneighbours.repository.DatabaseManager;
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
            appContext.setCurrentUser(restored);
            showDashboard();
            return;
        }

        // Affiche immédiatement une fenêtre d'accueil (le navigateur va s'ouvrir pour le login).
        showWaiting("Connexion", "Ouverture du navigateur pour la connexion…");

        // Lance le login navigateur sur un thread d'arrière-plan.
        Task<User> login = new Task<>() {
            @Override
            protected User call() throws Exception {
                return appContext.getAuthService().loginViaBrowser();
            }
        };
        login.setOnSucceeded(e -> {
            User user = login.getValue();
            appContext.setCurrentUser(user);
            SessionConfig.saveLastUser(user);
            showDashboard();
        });
        login.setOnFailed(e -> {
            Throwable t = login.getException();
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            showWaiting("Connexion échouée",
                    "Impossible de s'authentifier : " + msg + "\nFermez la fenêtre pour quitter.");
        });
        Thread t = new Thread(login, "sso-browser-login");
        t.setDaemon(true);
        t.start();
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
            primaryStage.setTitle(title + " — Connected Neighbours Admin");
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

                Scene scene = new Scene(root, 1280, 800);
                ThemeManager.applyTheme(scene);

                primaryStage.setTitle("Connected Neighbours — Admin");
                primaryStage.setScene(scene);
                primaryStage.setUserData(this);
                primaryStage.show();
            } catch (Exception e) {
                throw new RuntimeException("Impossible de charger dashboard.fxml", e);
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

                Scene scene = new Scene(root, 1280, 800);
                ThemeManager.applyTheme(scene);

                primaryStage.setTitle("Incidents — Connected Neighbours Admin");
                primaryStage.setScene(scene);
                primaryStage.setUserData(this);
                primaryStage.show();
            } catch (Exception e) {
                throw new RuntimeException("Impossible de charger incidents.fxml", e);
            }
        });
    }

    /**
     * Initialise le SyncService s'il n'est pas encore créé/démarré.
     */
    private void ensureSyncService() {
        if (syncService == null) {
            syncService = new SyncService(appContext.getApiClient());
            syncService.start();
        }
    }

    /**
     * Relance le login navigateur (utilisé par le bouton Déconnexion et en
     * cas d'expiration du token). Le navigateur tentera le refresh silencieux
     * si le cookie refresh est encore valide (≤7j) — pas de mot de passe à
     * retaper dans ce cas.
     */
    public void backToLogin() {
        if (syncService != null) {
            syncService.stop();
            syncService = null;
        }
        appContext.logout();

        showWaiting("Connexion", "Ouverture du navigateur pour la connexion…");

        Task<User> login = new Task<>() {
            @Override
            protected User call() throws Exception {
                return appContext.getAuthService().loginViaBrowser();
            }
        };
        login.setOnSucceeded(e -> {
            User user = login.getValue();
            appContext.setCurrentUser(user);
            SessionConfig.saveLastUser(user);
            showDashboard();
        });
        login.setOnFailed(e -> {
            Throwable t = login.getException();
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            showWaiting("Connexion échouée",
                    "Impossible de s'authentifier : " + msg + "\nFermez la fenêtre pour quitter.");
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
