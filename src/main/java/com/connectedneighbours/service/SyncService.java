package com.connectedneighbours.service;

import com.connectedneighbours.config.ApiConfig;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class SyncService {

    private static final int SYNC_INTERVAL_SECONDS = 30;

    private final IncidentRepository incidentRepo;
    private final UserRepository userRepo;
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
                JacksonConfig.get(),
                SyncService::defaultConnectivityCheck,
                javafx.application.Platform::runLater
        );
    }

    SyncService(
            ApiClient apiClient,
            IncidentRepository incidentRepo,
            UserRepository userRepo,
            ObjectMapper mapper,
            ConnectivityChecker connectivityChecker,
            UiExecutor uiExecutor
    ) {
        this.apiClient = apiClient;
        this.incidentRepo = incidentRepo;
        this.userRepo = userRepo;
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
            pushLocalUsers();
            pullRemoteUsers();
            notifyStatus(SyncStatus.SUCCESS);
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

    private void pushLocalUsers() {
        List<User> allUsers = userRepo.findAll();

        for (User user : allUsers) {
            try {
                String existingJson = apiClient.get("/users/" + user.getId());

                if (existingJson != null) {
                    User remote = mapper.readValue(existingJson, User.class);
                    User resolved = resolveConflictUser(user, remote);
                    apiClient.put("/users/" + resolved.getId(), resolved);
                } else {
                    apiClient.post("/users", user);
                }

            } catch (IOException e) {
                java.util.logging.Logger.getLogger(SyncService.class.getName()).log(
                        java.util.logging.Level.WARNING,
                        "Failed to sync local user with id " + user.getId(),
                        e
                );
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
