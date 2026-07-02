package com.connectedneighbours;

import com.connectedneighbours.controller.DashboardController;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.DatabaseManager;
import com.connectedneighbours.service.SyncService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
            appContext.setCurrentUser(login.getValue());
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

    /**
     * Affiche une fenêtre simple avec un message
     */
    private void showWaiting(String title, String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Label label = new javafx.scene.control.Label(message);
            label.setWrapText(true);
            label.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
            javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(label);
            root.setPadding(new javafx.geometry.Insets(24));
            root.setStyle("-fx-background-color: #f0f2f5;");
            Scene scene = new Scene(root, 480, 200);
            applyTheme(scene);
            primaryStage.setTitle(title + " — Connected Neighbours Admin");
            primaryStage.setScene(scene);
            primaryStage.show();
        });
    }

    private void showDashboard() {
        Platform.runLater(() -> {
            try {
                // Construit le SyncService maintenant que l'ApiClient a un token supplier.
                syncService = new SyncService(appContext.getApiClient());
                syncService.start();

                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/com/connectedneighbours/fxml/dashboard.fxml")
                );
                loader.setControllerFactory(cls -> {
                    if (cls == DashboardController.class)
                        return new DashboardController(appContext, syncService);
                    try {
                        return cls.getDeclaredConstructors()[0].newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                Parent root = loader.load();

                Scene scene = new Scene(root, 1280, 800);
                applyTheme(scene);

                primaryStage.setTitle("Connected Neighbours — Admin");
                primaryStage.setScene(scene);
                primaryStage.setUserData(this);
                primaryStage.show();
            } catch (Exception e) {
                throw new RuntimeException("Impossible de charger dashboard.fxml", e);
            }
        });
    }

    private void applyTheme(Scene scene) {
        try {
            scene.getStylesheets().add(
                    getClass().getResource("/com/connectedneighbours/css/theme-light.css").toExternalForm()
            );
        } catch (Exception ignored) {
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
            appContext.setCurrentUser(login.getValue());
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
        if (syncService != null) syncService.stop();
        DatabaseManager.close();
    }
}
