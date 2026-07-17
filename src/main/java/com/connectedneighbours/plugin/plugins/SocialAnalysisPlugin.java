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
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SocialAnalysisPlugin implements Plugin {

    private static final Logger LOG = Logger.getLogger(SocialAnalysisPlugin.class.getName());
    private static final ObjectMapper MAPPER = JacksonConfig.get();

    @Override public String getName() { return "SocialAnalysisPlugin"; }
    @Override public String getVersion() { return "1.0.0"; }
    @Override public void initialize() { LOG.info("SocialAnalysisPlugin initialized"); }
    @Override public void shutdown() { LOG.info("SocialAnalysisPlugin shutdown"); }

    @Override
    public void execute(Object context) {
        if (!(context instanceof AppContext ctx)) {
            LOG.warning("SocialAnalysisPlugin: contexte invalide");
            return;
        }
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setTitle("Analyse sociale — Connected Neighbours");
            stage.setMinWidth(960);
            stage.setMinHeight(680);

            TabPane tabPane = new TabPane();
            tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

            tabPane.getTabs().addAll(
                    buildUsersTab(ctx),
                    buildIncidentsTab(ctx),
                    buildConversationsTab(ctx),
                    buildActivityTab(ctx)
            );

            Scene scene = new Scene(tabPane, 960, 680);
            ThemeManager.applyTheme(scene);
            stage.setScene(scene);
            stage.show();
        });
    }

    private VBox loadingBox() {
        Label lbl = new Label("Chargement...");
        lbl.setStyle("-fx-font-size: 14px; -fx-padding: 20;");
        return new VBox(lbl);
    }

    private VBox errorBox(String msg) {
        Label lbl = new Label(msg);
        lbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #c0392b; -fx-padding: 20;");
        return new VBox(lbl);
    }

    private void fetchAsync(AppContext ctx, String endpoint, java.util.function.Consumer<JsonNode> onSuccess) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return MAPPER.readTree(ctx.getApiClient().get(endpoint));
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Erreur API " + endpoint, e);
                return null;
            }
        }).thenAcceptAsync(node -> Platform.runLater(() -> onSuccess.accept(node)));
    }

    private static TableColumn<JsonNode, String> jsonCol(String header, String field) {
        TableColumn<JsonNode, String> col = new TableColumn<>(header);
        col.setCellValueFactory(data -> {
            JsonNode n = data.getValue();
            String val = (n != null && n.has(field) && !n.get(field).isNull())
                    ? n.get(field).asText() : "";
            return new SimpleStringProperty(val);
        });
        return col;
    }

    private static PieChart buildPieChart(JsonNode obj, String title) {
        PieChart chart = new PieChart();
        chart.setTitle(title);
        if (obj != null && obj.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                chart.getData().add(new PieChart.Data(e.getKey(), e.getValue().asDouble()));
            }
        }
        chart.setLabelsVisible(true);
        chart.setPrefSize(350, 280);
        return chart;
    }

    private Label sectionTitle(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 8 0 4 0;");
        return lbl;
    }

    private Tab buildUsersTab(AppContext ctx) {
        Tab tab = new Tab("Utilisateurs");
        tab.setClosable(false);
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        VBox placeholder = loadingBox();
        root.setCenter(placeholder);
        tab.setContent(root);

        tab.selectedProperty().addListener((obs, was, now) -> {
            if (now && root.getCenter() == placeholder) {
                fetchAsync(ctx, "/users?limit=100", json -> Platform.runLater(() -> {
                    if (json == null || !json.has("data")) {
                        root.setCenter(errorBox("Impossible de charger les utilisateurs."));
                        return;
                    }
                    JsonNode data = json.get("data");
                    int total = json.get("total").asInt();

                    VBox content = new VBox(14);
                    content.setPadding(new Insets(4, 0, 0, 0));

                    content.getChildren().add(sectionTitle(
                            String.format("Total utilisateurs : %d  |  Affichés : %d", total, data.size())));

                    PieChart roleChart = buildPieChart(countByKey(data, "role"), "Répartition par rôle");
                    if (!roleChart.getData().isEmpty()) {
                        roleChart.setLabelsVisible(true);
                        content.getChildren().add(roleChart);
                    }

                    TableView<JsonNode> table = new TableView<>();
                    table.setPrefHeight(320);
                    table.getColumns().addAll(
                            jsonCol("Email", "email"),
                            jsonCol("Prénom", "firstName"),
                            jsonCol("Nom", "lastName"),
                            jsonCol("Rôle", "role"),
                            jsonCol("Solde", "balance"),
                            jsonCol("District", "districtId")
                    );
                    ObservableList<JsonNode> items = FXCollections.observableArrayList();
                    for (JsonNode node : data) items.add(node);
                    table.setItems(items);
                    VBox.setVgrow(table, Priority.ALWAYS);
                    content.getChildren().add(table);

                    root.setCenter(content);
                }));
            }
        });
        return tab;
    }

    private Tab buildIncidentsTab(AppContext ctx) {
        Tab tab = new Tab("Incidents");
        tab.setClosable(false);
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        VBox placeholder = loadingBox();
        root.setCenter(placeholder);
        tab.setContent(root);

        tab.selectedProperty().addListener((obs, was, now) -> {
            if (now && root.getCenter() == placeholder) {
                fetchAsync(ctx, "/incidents/stats", json -> Platform.runLater(() -> {
                    if (json == null) {
                        root.setCenter(errorBox("Impossible de charger les statistiques d'incidents."));
                        return;
                    }
                    VBox content = new VBox(16);
                    content.setPadding(new Insets(4, 0, 0, 0));

                    int total = json.get("total").asInt();
                    content.getChildren().add(sectionTitle("Total incidents : " + total));

                    HBox chartsBox = new HBox(24);

                    PieChart statusChart = buildPieChart(json.get("byStatus"), "Par statut");
                    chartsBox.getChildren().add(statusChart);

                    PieChart catChart = buildPieChart(json.get("byCategory"), "Par catégorie");
                    chartsBox.getChildren().add(catChart);

                    content.getChildren().add(chartsBox);

                    TableView<JsonNode> table = new TableView<>();
                    table.setPrefHeight(280);
                    TableColumn<JsonNode, String> catCol = new TableColumn<>("Catégorie");
                    catCol.setCellValueFactory(d -> {
                        JsonNode n = d.getValue();
                        return new SimpleStringProperty(n.has("cat") ? n.get("cat").asText() : "");
                    });
                    TableColumn<JsonNode, String> countCol = new TableColumn<>("Nombre");
                    countCol.setCellValueFactory(d -> {
                        JsonNode n = d.getValue();
                        return new SimpleStringProperty(n.has("cnt") ? String.valueOf(n.get("cnt").asInt()) : "0");
                    });
                    table.getColumns().addAll(catCol, countCol);

                    JsonNode byCat = json.get("byCategory");
                    ObservableList<JsonNode> items = FXCollections.observableArrayList();
                    if (byCat != null) {
                        Iterator<Map.Entry<String, JsonNode>> it = byCat.fields();
                        while (it.hasNext()) {
                            Map.Entry<String, JsonNode> e = it.next();
                            items.add(MAPPER.createObjectNode()
                                    .put("cat", e.getKey())
                                    .put("cnt", e.getValue().asInt()));
                        }
                    }
                    table.setItems(items);
                    VBox.setVgrow(table, Priority.ALWAYS);
                    content.getChildren().add(table);

                    root.setCenter(content);
                }));
            }
        });
        return tab;
    }

    private Tab buildConversationsTab(AppContext ctx) {
        Tab tab = new Tab("Messagerie");
        tab.setClosable(false);
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        VBox placeholder = loadingBox();
        root.setCenter(placeholder);
        tab.setContent(root);

        tab.selectedProperty().addListener((obs, was, now) -> {
            if (now && root.getCenter() == placeholder) {
                fetchAsync(ctx, "/conversations?limit=100", json -> Platform.runLater(() -> {
                    if (json == null || !json.has("data")) {
                        root.setCenter(errorBox("Impossible de charger les conversations."));
                        return;
                    }
                    JsonNode data = json.get("data");
                    int total = json.get("total").asInt();
                    int direct = 0;
                    int group = 0;
                    for (JsonNode c : data) {
                        if ("group".equals(c.get("type").asText())) group++;
                        else direct++;
                    }

                    VBox content = new VBox(14);
                    content.setPadding(new Insets(4, 0, 0, 0));

                    content.getChildren().add(sectionTitle(
                            String.format("Total : %d  |  Directes : %d  |  Groupe : %d",
                                    total, direct, group)));

                    TableView<JsonNode> table = new TableView<>();
                    table.setPrefHeight(460);
                    TableColumn<JsonNode, String> idCol = jsonCol("ID", "id");
                    idCol.setPrefWidth(260);
                    TableColumn<JsonNode, String> typeCol = jsonCol("Type", "type");
                    typeCol.setPrefWidth(80);
                    TableColumn<JsonNode, String> partCol = new TableColumn<>("Participants");
                    partCol.setCellValueFactory(d -> {
                        JsonNode n = d.getValue();
                        int cnt = (n != null && n.has("participants"))
                                ? n.get("participants").size() : 0;
                        return new SimpleStringProperty(String.valueOf(cnt));
                    });
                    partCol.setPrefWidth(100);
                    TableColumn<JsonNode, String> nameCol = jsonCol("Nom", "name");
                    nameCol.setPrefWidth(140);
                    TableColumn<JsonNode, String> districtCol = jsonCol("District", "districtId");
                    districtCol.setPrefWidth(260);
                    TableColumn<JsonNode, String> lastCol = jsonCol("Dernier message", "lastMessageAt");
                    lastCol.setPrefWidth(180);
                    table.getColumns().addAll(idCol, typeCol, partCol, nameCol, districtCol, lastCol);

                    ObservableList<JsonNode> items = FXCollections.observableArrayList();
                    for (JsonNode node : data) items.add(node);
                    table.setItems(items);
                    VBox.setVgrow(table, Priority.ALWAYS);
                    content.getChildren().add(table);

                    root.setCenter(content);
                }));
            }
        });
        return tab;
    }

    private Tab buildActivityTab(AppContext ctx) {
        Tab tab = new Tab("Activité");
        tab.setClosable(false);
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        VBox placeholder = loadingBox();
        root.setCenter(placeholder);
        tab.setContent(root);

        tab.selectedProperty().addListener((obs, was, now) -> {
            if (now && root.getCenter() == placeholder) {
                fetchAsync(ctx, "/events?limit=100", eventsJson -> {
                    fetchAsync(ctx, "/votes?limit=100", votesJson -> Platform.runLater(() -> {
                        VBox content = new VBox(16);
                        content.setPadding(new Insets(4, 0, 0, 0));

                        if (eventsJson != null && eventsJson.has("data")) {
                            JsonNode evts = eventsJson.get("data");
                            int evtTotal = eventsJson.get("total").asInt();

                            content.getChildren().add(sectionTitle(
                                    "Événements  —  Total : " + evtTotal + "  |  Affichés : " + evts.size()));

                            PieChart evtStatusChart = buildPieChart(
                                    countByKey(evts, "status"), "Par statut");
                            if (!evtStatusChart.getData().isEmpty()) {
                                content.getChildren().add(evtStatusChart);
                            }

                            TableView<JsonNode> evtTable = new TableView<>();
                            evtTable.setPrefHeight(200);
                            evtTable.getColumns().addAll(
                                    jsonCol("Titre", "title"),
                                    jsonCol("Lieu", "location"),
                                    jsonCol("Statut", "status"),
                                    jsonCol("Date", "eventDate"),
                                    jsonCol("Places dispo.", "remainingSeats")
                            );
                            ObservableList<JsonNode> evtItems = FXCollections.observableArrayList();
                            for (JsonNode node : evts) evtItems.add(node);
                            evtTable.setItems(evtItems);
                            content.getChildren().add(evtTable);
                        }

                        if (votesJson != null && votesJson.has("data")) {
                            JsonNode votes = votesJson.get("data");
                            int voteTotal = votesJson.get("total").asInt();

                            content.getChildren().add(sectionTitle(
                                    "Votes  —  Total : " + voteTotal + "  |  Affichés : " + votes.size()));

                            PieChart voteStatusChart = buildPieChart(
                                    countByKey(votes, "status"), "Par statut");
                            if (!voteStatusChart.getData().isEmpty()) {
                                content.getChildren().add(voteStatusChart);
                            }

                            TableView<JsonNode> voteTable = new TableView<>();
                            voteTable.setPrefHeight(200);
                            voteTable.getColumns().addAll(
                                    jsonCol("Question", "question"),
                                    jsonCol("Type", "voteType"),
                                    jsonCol("Statut", "status"),
                                    jsonCol("Début", "startDate"),
                                    jsonCol("Fin", "endDate")
                            );
                            ObservableList<JsonNode> voteItems = FXCollections.observableArrayList();
                            for (JsonNode node : votes) voteItems.add(node);
                            voteTable.setItems(voteItems);
                            VBox.setVgrow(voteTable, Priority.ALWAYS);
                            content.getChildren().add(voteTable);
                        }

                        if (content.getChildren().isEmpty()) {
                            content.getChildren().add(errorBox(
                                    "Aucune donnée d'activité disponible."));
                        }

                        root.setCenter(content);
                    }));
                });
            }
        });
        return tab;
    }

    private static JsonNode countByKey(JsonNode array, String key) {
        var obj = MAPPER.createObjectNode();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String val = item.has(key) && !item.get(key).isNull()
                        ? item.get(key).asText() : "inconnu";
                int cur = obj.has(val) ? obj.get(val).asInt() : 0;
                obj.put(val, cur + 1);
            }
        }
        return obj;
    }
}
