package com.connectedneighbours.plugin.plugins;

import com.connectedneighbours.AppContext;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.plugin.Plugin;
import com.connectedneighbours.theme.ThemeManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocalCalendarPlugin implements Plugin {

    private static final Logger LOG = Logger.getLogger(LocalCalendarPlugin.class.getName());
    private static final ObjectMapper MAPPER = JacksonConfig.get();

    @Override public String getName() { return "LocalCalendarPlugin"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public void initialize() { LOG.info("LocalCalendarPlugin initialized"); }
    @Override public void shutdown() { LOG.info("LocalCalendarPlugin shutdown"); }

    @Override
    public void execute(Object context) {
        if (!(context instanceof AppContext ctx)) {
            LOG.warning("LocalCalendarPlugin: contexte invalide");
            return;
        }
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setTitle("Calendrier local — Connected Neighbours");
            stage.setMinWidth(950);
            stage.setMinHeight(600);

            BorderPane root = new BorderPane();
            root.setPadding(new Insets(16));
            root.setStyle("-fx-background-color: -fx-background;");

            VBox header = new VBox(12);
            header.setPadding(new Insets(0, 0, 8, 0));
            Label title = new Label("Calendrier des événements du quartier");
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
            header.getChildren().add(title);

            HBox filters = new HBox(12);
            Label filterLabel = new Label("Filtrer par statut :");
            filterLabel.setStyle("-fx-font-size: 13px;");
            ComboBox<String> statusFilter = new ComboBox<>();
            statusFilter.getItems().addAll("Tous", "upcoming", "ongoing", "completed", "cancelled");
            statusFilter.setValue("Tous");
            statusFilter.setStyle("-fx-font-size: 12px;");
            filters.getChildren().addAll(filterLabel, statusFilter);
            header.getChildren().add(filters);

            root.setTop(header);

            TableView<JsonNode> table = new TableView<>();
            table.setPrefHeight(350);

            TableColumn<JsonNode, String> dateCol = col("Date", "eventDate");
            dateCol.setPrefWidth(170);
            TableColumn<JsonNode, String> titleCol = col("Titre", "title");
            titleCol.setPrefWidth(220);
            TableColumn<JsonNode, String> locCol = col("Lieu", "location");
            locCol.setPrefWidth(180);
            TableColumn<JsonNode, String> statusCol = col("Statut", "status");
            statusCol.setPrefWidth(100);
            TableColumn<JsonNode, String> seatsCol = new TableColumn<>("Places");
            seatsCol.setCellValueFactory(d -> {
                JsonNode n = d.getValue();
                int rem = (n != null && n.has("remainingSeats")) ? n.get("remainingSeats").asInt() : 0;
                int tot = (n != null && n.has("totalSeats")) ? n.get("totalSeats").asInt() : 0;
                return new SimpleStringProperty(rem + " / " + tot);
            });
            seatsCol.setPrefWidth(80);
            TableColumn<JsonNode, String> regCol = new TableColumn<>("Inscrits");
            regCol.setCellValueFactory(d -> {
                JsonNode n = d.getValue();
                int cnt = (n != null && n.has("registrants")) ? n.get("registrants").size() : 0;
                return new SimpleStringProperty(String.valueOf(cnt));
            });
            regCol.setPrefWidth(70);
            table.getColumns().addAll(dateCol, titleCol, locCol, statusCol, seatsCol, regCol);

            VBox detailPanel = new VBox(10);
            detailPanel.setPadding(new Insets(12));
            detailPanel.setStyle("-fx-background-color: derive(-fx-background, -5%); -fx-background-radius: 6;");
            detailPanel.setVisible(false);
            detailPanel.setPrefHeight(180);

            Label detailTitle = new Label();
            detailTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
            Label detailMeta = new Label();
            detailMeta.setStyle("-fx-font-size: 12px;");
            detailMeta.setWrapText(true);
            TextArea detailDesc = new TextArea();
            detailDesc.setEditable(false);
            detailDesc.setWrapText(true);
            detailDesc.setPrefRowCount(4);
            detailDesc.setStyle("-fx-font-size: 12px;");
            detailPanel.getChildren().addAll(detailTitle, detailMeta, detailDesc);

            table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
                if (sel == null) {
                    detailPanel.setVisible(false);
                    return;
                }
                detailTitle.setText(sel.has("title") ? sel.get("title").asText() : "");
                detailMeta.setText(String.format("Lieu : %s  |  Statut : %s  |  Date : %s",
                        sel.has("location") ? sel.get("location").asText() : "-",
                        sel.has("status") ? sel.get("status").asText() : "-",
                        sel.has("eventDate") ? sel.get("eventDate").asText() : "-"));
                detailDesc.setText(sel.has("description") ? sel.get("description").asText() : "");
                detailPanel.setVisible(true);
            });

            VBox centerBox = new VBox(8);
            centerBox.getChildren().addAll(table, detailPanel);
            VBox.setVgrow(table, Priority.ALWAYS);
            root.setCenter(centerBox);

            Label loadingLabel = new Label("Chargement des événements...");
            loadingLabel.setStyle("-fx-font-size: 14px; -fx-padding: 20;");
            root.setCenter(loadingLabel);

            CompletableFuture.supplyAsync(() -> fetchAllPages(ctx))
                    .thenAcceptAsync(allEvents -> Platform.runLater(() -> {
                        ObservableList<JsonNode> masterList = FXCollections.observableArrayList(allEvents);

                        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> {
                            if ("Tous".equals(newVal)) {
                                table.setItems(masterList);
                            } else {
                                ObservableList<JsonNode> filtered = FXCollections.observableArrayList();
                                for (JsonNode e : masterList) {
                                    if (newVal.equals(e.get("status").asText())) {
                                        filtered.add(e);
                                    }
                                }
                                table.setItems(filtered);
                            }
                        });

                        table.setItems(masterList);
                        root.setCenter(centerBox);
                    }), Platform::runLater);

            Scene scene = new Scene(root, 950, 600);
            ThemeManager.applyTheme(scene);
            stage.setScene(scene);
            stage.show();
        });
    }

    private List<JsonNode> fetchAllPages(AppContext ctx) {
        List<JsonNode> all = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;
        while (hasMore) {
            try {
                String json = ctx.getApiClient().get("/events?page=" + page + "&limit=100");
                JsonNode root = MAPPER.readTree(json);
                JsonNode data = root.get("data");
                if (data != null && data.isArray()) {
                    for (JsonNode item : data) all.add(item);
                }
                int total = root.get("total").asInt();
                hasMore = (page * 100) < total;
                page++;
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Erreur fetch events page " + page, e);
                hasMore = false;
            }
        }
        all.sort((a, b) -> {
            String da = a.has("eventDate") ? a.get("eventDate").asText() : "";
            String db = b.has("eventDate") ? b.get("eventDate").asText() : "";
            return da.compareTo(db);
        });
        return all;
    }

    private static TableColumn<JsonNode, String> col(String header, String field) {
        TableColumn<JsonNode, String> c = new TableColumn<>(header);
        c.setCellValueFactory(d -> {
            JsonNode n = d.getValue();
            String val = (n != null && n.has(field) && !n.get(field).isNull())
                    ? n.get(field).asText() : "";
            return new SimpleStringProperty(val);
        });
        return c;
    }
}
