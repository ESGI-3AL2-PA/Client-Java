package com.connectedneighbours;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/connectedneighbours/fxml/dashboard.fxml")
        );
        Scene scene = new Scene(loader.load(), 1280, 800);

        scene.getStylesheets().add(
                getClass().getResource("/com/connectedneighbours/css/theme-light.css").toExternalForm()
        );

        primaryStage.setTitle("Connected Neighbours — Admin");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}