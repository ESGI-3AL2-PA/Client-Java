package com.connectedneighbours.service;

import com.connectedneighbours.config.ApiConfig;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.District;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.Statistic;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.repository.ApiException;
import com.connectedneighbours.repository.DistrictRepository;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.StatisticRepository;
import com.connectedneighbours.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class SyncService {

    private static final int SYNC_INTERVAL_SECONDS = 30;
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final IncidentRepository incidentRepo;
    private final UserRepository userRepo;
    private final DistrictRepository districtRepo;
    private final StatisticRepository statisticRepo;
    private final ApiClient apiClient;
    private final ObjectMapper mapper;
    private final ConnectivityChecker connectivityChecker;
    private final UiExecutor uiExecutor;
    private Timer timer;
    private TimerTask syncTask;

    private boolean isSyncing = false;

    private SyncStatusListener statusListener;

    public SyncService(ApiClient apiClient) {
        this(
                apiClient,
                new IncidentRepository(),
                new UserRepository(),
                new DistrictRepository(),
                JacksonConfig.get(),
                SyncService::defaultConnectivityCheck,
                javafx.application.Platform::runLater
        );
    }

    SyncService(
            ApiClient apiClient,
            IncidentRepository incidentRepo,
            UserRepository userRepo,
            DistrictRepository districtRepo,
            ObjectMapper mapper,
            ConnectivityChecker connectivityChecker,
            UiExecutor uiExecutor
    ) {
        this.apiClient = apiClient;
        this.incidentRepo = incidentRepo;
        this.userRepo = userRepo;
        this.districtRepo = districtRepo;
        this.statisticRepo = new StatisticRepository();
        this.mapper = mapper;
        this.connectivityChecker = connectivityChecker;
        this.uiExecutor = uiExecutor;
    }

    private static boolean defaultConnectivityCheck() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ApiConfig.getHost(), ApiConfig.getPortForSocket()), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void start() {
        if (timer != null) {
            return;
        }
        timer = new Timer(true);
        syncTask = new TimerTask() {
            @Override
            public void run() {
                syncCycle();
            }
        };
        timer.schedule(syncTask, 0, SYNC_INTERVAL_SECONDS * 1000L);
    }

    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
    }

    public void syncNow() {
        Thread thread = new Thread(this::syncCycle);
        thread.start();
    }

    void syncCycle() {
        if (isSyncing) {
            return;
        }

        if (!connectivityChecker.isOnline()) {
            notifyStatus(SyncStatus.OFFLINE);
            return;
        }

        isSyncing = true;
        notifyStatus(SyncStatus.SYNCING);

        try {
            pushLocalIncidents();
            pullRemoteIncidents();
            pullRemoteUsers();
            pullRemoteDistricts();
            pullStatistics();
            notifyStatus(SyncStatus.SUCCESS);
        } catch (com.connectedneighbours.auth.exception.TokenUnavailableException e) {
            // plus d'access token --> relance le login navigateur.
            notifyStatus(SyncStatus.AUTH_REQUIRED);
        } catch (Exception e) {
            notifyStatus(SyncStatus.ERROR);
        } finally {
            isSyncing = false;
        }
    }

    private void pushLocalIncidents() throws SQLException, IOException {
        List<Incident> unsynced = incidentRepo.findUnsynced();

        for (Incident incident : unsynced) {
            try {
                String existingJson = apiClient.get("/incidents/" + incident.getId());

                if (existingJson != null) {
                    Incident remote = mapper.readValue(existingJson, Incident.class);
                    Incident resolved = resolveConflictIncident(incident, remote);
                    apiClient.put("/incidents/" + resolved.getId(), resolved);
                } else {
                    apiClient.post("/incidents", incident);
                }

                incident.setSynced(true);
                incidentRepo.update(incident);

            } catch (ApiException e) {
                if (e.isNotFound()) {
                    // L'incident n'existe pas encore sur le serveur : le créer.
                    try {
                        apiClient.post("/incidents", incident);
                        incident.setSynced(true);
                        incidentRepo.update(incident);
                    } catch (IOException postEx) {
                        java.util.logging.Logger.getLogger(SyncService.class.getName()).log(
                                java.util.logging.Level.WARNING,
                                "Failed to create local incident with id " + incident.getId(),
                                postEx
                        );
                    }
                } else {
                    java.util.logging.Logger.getLogger(SyncService.class.getName()).log(
                            java.util.logging.Level.WARNING,
                            "Failed to sync local incident with id " + incident.getId(),
                            e
                    );
                }
            } catch (IOException e) {
                java.util.logging.Logger.getLogger(SyncService.class.getName()).log(
                        java.util.logging.Level.WARNING,
                        "Failed to sync local incident with id " + incident.getId(),
                        e
                );
            }
        }
    }

    public void pullRemoteIncidents() throws IOException, SQLException {
        String json = apiClient.get("/incidents?source=remote");
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.has("data") ? root.get("data") : root;
        Incident[] remoteArray = mapper.treeToValue(dataNode, Incident[].class);
        List<Incident> remoteList = java.util.Arrays.asList(remoteArray);

        for (Incident remote : remoteList) {
            Optional<Incident> existing = incidentRepo.findById(remote.getId());

            if (existing.isEmpty()) {
                // Nouveau sur le serveur : insérer localement
                remote.setSynced(true);
                incidentRepo.save(remote);
            } else {
                // Existe déjà : résoudre le conflit
                Incident resolved = resolveConflictIncident(existing.get(), remote);
                resolved.setSynced(true);
                incidentRepo.update(resolved);
            }
        }
    }

    public void pullRemoteUsers() throws IOException {
        String json = apiClient.get("/users?source=remote");
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.has("data") ? root.get("data") : root;
        User[] remoteArray = mapper.treeToValue(dataNode, User[].class);
        List<User> remoteList = java.util.Arrays.asList(remoteArray);

        for (User remote : remoteList) {
            Optional<User> existing = userRepo.findById(remote.getId());

            if (existing.isEmpty()) {
                // Nouveau sur le serveur : insérer localement
                userRepo.save(remote);
            } else {
                // Existe déjà : résoudre le conflit
                User resolved = resolveConflictUser(existing.get(), remote);
                userRepo.update(resolved);
            }
        }
    }

    public void pullRemoteDistricts() throws IOException {
        String json = apiClient.get("/districts");
        JsonNode root = mapper.readTree(json);
        JsonNode dataNode = root.has("data") ? root.get("data") : root;
        District[] remoteArray = mapper.treeToValue(dataNode, District[].class);
        List<District> remoteList = java.util.Arrays.asList(remoteArray);

        for (District remote : remoteList) {
            Optional<District> existing = districtRepo.findById(remote.getId());

            if (existing.isEmpty()) {
                districtRepo.save(remote);
            } else {
                districtRepo.update(remote);
            }
        }
    }

    private void pullStatistics() {
        String today = LocalDate.now().format(PERIOD_FMT);

        fetchIncidentStats(today);
        fetchTotal("/users?page=1&limit=1", "users.total", today);
        fetchTotal("/listings?page=1&limit=1", "listings.total", today);
        fetchTotal("/events?page=1&limit=1", "events.total", today);
        fetchTotal("/votes?page=1&limit=1", "votes.total", today);
    }

    private void fetchIncidentStats(String period) {
        try {
            JsonNode root = mapper.readTree(apiClient.get("/incidents/stats"));

            upsertStatistic("incidents.total", root.path("total").asDouble(0), period);
            root.path("byStatus").fields().forEachRemaining(entry ->
                    upsertStatistic("incidents.status." + entry.getKey(), entry.getValue().asDouble(0), period));
            root.path("byCategory").fields().forEachRemaining(entry ->
                    upsertStatistic("incidents.category." + entry.getKey(), entry.getValue().asDouble(0), period));
        } catch (Exception e) {
            logStatsWarning("/incidents/stats", e);
        }
    }

    private void fetchTotal(String endpoint, String metricKey, String period) {
        try {
            JsonNode root = mapper.readTree(apiClient.get(endpoint));
            upsertStatistic(metricKey, root.path("total").asDouble(0), period);
        } catch (Exception e) {
            logStatsWarning(endpoint, e);
        }
    }

    private void upsertStatistic(String metricKey, double value, String period) {
        List<Statistic> existing = statisticRepo.findByMetricKeyAndPeriod(metricKey, period);
        if (existing.isEmpty()) {
            statisticRepo.save(new Statistic(metricKey, value, period));
        } else {
            Statistic stat = existing.get(0);
            stat.setMetricValue(value);
            stat.setRecordedAt(LocalDateTime.now());
            statisticRepo.update(stat);
        }
    }

    private void logStatsWarning(String endpoint, Exception e) {
        java.util.logging.Logger.getLogger(SyncService.class.getName()).log(
                java.util.logging.Level.WARNING,
                "Failed to pull statistics from " + endpoint,
                e
        );
    }

    /**
     * Résolution de conflit : Le dernier a raison
     *
     * @param local  Incident en local
     * @param remote Incident distant
     * @return L'incident le plus récent entre les deux
     */
    private Incident resolveConflictIncident(Incident local, Incident remote) {
        if (local.getUpdatedAt() == null) return remote;
        if (remote.getUpdatedAt() == null) return local;

        if (local.getUpdatedAt().isAfter(remote.getUpdatedAt())) {
            return local;
        } else {
            return remote;
        }
    }

    private User resolveConflictUser(User local, User remote) {
        if (local.getUpdatedAt() == null) return remote;
        if (remote.getUpdatedAt() == null) return local;

        if (local.getUpdatedAt().isAfter(remote.getUpdatedAt())) {
            return local;
        } else {
            return remote;
        }
    }

    private void notifyStatus(SyncStatus status) {
        if (statusListener != null) {
            uiExecutor.execute(() -> statusListener.onStatusChanged(status));
        }
    }

    public void setStatusListener(SyncStatusListener listener) {
        this.statusListener = listener;
    }
}
