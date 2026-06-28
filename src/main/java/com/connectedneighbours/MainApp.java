package com.connectedneighbours;

import com.connectedneighbours.model.User;
import com.connectedneighbours.controller.DashboardController;
import com.connectedneighbours.controller.LoginController;
import com.connectedneighbours.repository.DatabaseManager;
import com.connectedneighbours.service.SyncService;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

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

        // 1) Tentative de refresh silencieux (cookie persistant) sur un thread
        //    d'arrière-plan : si OK on charge le dashboard, sinon l'écran login.
        Task<Boolean> bootstrap = new Task<>() {
            @Override
            protected Boolean call() {
                return appContext.getAuthService().tryRefresh();
            }
        };
        bootstrap.setOnSucceeded(e -> {
            if (Boolean.TRUE.equals(bootstrap.getValue())) {
                // Charge le user courant puis le dashboard.
                loadCurrentUserAndShowDashboard();
            } else {
                showLogin();
            }
        });
        bootstrap.setOnFailed(e -> showLogin());

        Thread t = new Thread(bootstrap, "sso-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    private void loadCurrentUserAndShowDashboard() {
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                return appContext.getAuthService().fetchUserInfo();
            }
        };
        task.setOnSucceeded(e -> {
            appContext.setCurrentUser(task.getValue());
            showDashboard();
        });
        task.setOnFailed(e -> {
            // Le token a été rafraîchi mais /userinfo échoue → on retente le login.
            showLogin();
        });
        Thread t = new Thread(task, "sso-userinfo");
        t.setDaemon(true);
        t.start();
    }

    private void showLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/connectedneighbours/fxml/login.fxml")
            );
            Parent root = loader.load();
            LoginController controller = loader.getController();
            controller.setAppContext(appContext);
            controller.setOnSuccess(this::showDashboard);

            Scene scene = new Scene(root, 480, 600);
            applyTheme(scene);

            primaryStage.setTitle("Connexion — Connected Neighbours");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (IOException e) {
            throw new RuntimeException("Impossible de charger login.fxml", e);
        }
    }

    private void showDashboard() {
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
        } catch (IOException e) {
            throw new RuntimeException("Impossible de charger dashboard.fxml", e);
        }
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
     * Revient à l'écran de login après une déconnexion.
     */
    public void backToLogin() {
        if (syncService != null) {
            syncService.stop();
            syncService = null;
        }
        showLogin();
    }

    @Override
    public void stop() throws Exception {
        if (syncService != null) syncService.stop();
        DatabaseManager.close();
    }
}
