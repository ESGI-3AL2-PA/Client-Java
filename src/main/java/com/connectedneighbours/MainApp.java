package com.connectedneighbours;

import com.connectedneighbours.controller.DashboardController;
import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.repository.DatabaseManager;
import com.connectedneighbours.service.SyncService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    private SyncService syncService;
    private DatabaseManager databaseManager;
    private ApiClient apiClient;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        ApiClient apiClient = new ApiClient();
        syncService = new SyncService(apiClient);
        syncService.start();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/connectedneighbours/fxml/dashboard.fxml")
        );
        loader.setControllerFactory(cls -> {
            if (cls == DashboardController.class)
                return new DashboardController(syncService, apiClient);
            try {
                return cls.getDeclaredConstructors()[0].newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Scene scene = new Scene(loader.load(), 1280, 800);

        scene.getStylesheets().add(
                getClass().getResource("/com/connectedneighbours/css/theme-light.css").toExternalForm()
        );

        primaryStage.setTitle("Connected Neighbours — Admin");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        syncService.stop();
        DatabaseManager.close();
    }
}