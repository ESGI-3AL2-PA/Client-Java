package com.connectedneighbours.service;

import com.connectedneighbours.auth.exception.TokenUnavailableException;
import com.connectedneighbours.config.ApiConfig;
import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.config.SyncConfig;
import com.connectedneighbours.model.District;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.PendingChange;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.ApiException;
import com.connectedneighbours.repository.DistrictRepository;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.PendingChangesRepository;
import com.connectedneighbours.repository.SyncApiClient;
import com.connectedneighbours.repository.UserRepository;
import com.connectedneighbours.sync.ChangeEntry;
import com.connectedneighbours.sync.IngestEvent;
import com.connectedneighbours.sync.IngestResult;
import com.connectedneighbours.sync.SyncEntity;
import com.connectedneighbours.sync.SyncMapper;
import com.connectedneighbours.sync.SyncPayloads;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synchronisation hors-ligne entre la base H2 locale et MongoDB, via les
 * routes de sync de l'api.
 *
 * <p>Chaque cycle fait deux choses, toujours dans cet ordre :</p>
 * <ol>
 *   <li><b>push</b> — vide {@code pending_changes} dans {@code POST /ingest} ;</li>
 *   <li><b>pull</b> — lit la suite de {@code GET /changes} et l'applique à H2.</li>
 * </ol>
 *
 * <p>Il n'y a pas de bootstrap séparé : {@code since=0} <em>est</em> le
 * snapshot complet, donc un seul chemin de lecture à maintenir.</p>
 *
 * <p>Le lot poussé n'a pas besoin d'être compacté : {@code pending_changes}
 * porte déjà une ligne par enregistrement.</p>
 */
public class SyncService {

    private static final int SYNC_INTERVAL_SECONDS = 30;
    private static final int PUSH_BATCH_SIZE = 100;
    private static final int PULL_PAGE_SIZE = 200;

    /**
     * Garde-fou sur le drainage du flux : un premier démarrage télécharge un
     * snapshot complet, mais on ne monopolise pas un cycle indéfiniment — le
     * reste arrivera au tick suivant.
     */
    private static final int MAX_PULL_PAGES = 50;

    private static final Logger LOGGER = Logger.getLogger(SyncService.class.getName());

    private final SyncApiClient apiClient;
    private final PendingChangesRepository pendingRepo;
    private final IncidentRepository incidentRepo;
    private final UserRepository userRepo;
    private final DistrictRepository districtRepo;
    private final StatisticsService statisticsService;
    private final SyncConfig syncConfig;
    private final ObjectMapper mapper;
    private final ConnectivityChecker connectivityChecker;
    private final UiExecutor uiExecutor;

    /**
     * Ferme la fenêtre où {@code syncNow()} et un tick planifié pourraient
     * démarrer un cycle en même temps : seul celui qui gagne le
     * {@code compareAndSet} travaille.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledExecutorService executor;

    private SyncStatusListener statusListener;
    private Consumer<Integer> conflictListener;
    private Consumer<List<String>> rejectionListener;

    private volatile int pendingConflictCount;

    public SyncService(SyncApiClient apiClient) {
        this(
                apiClient,
                new PendingChangesRepository(),
                new IncidentRepository(),
                new UserRepository(),
                new DistrictRepository(),
                new StatisticsService(),
                new SyncConfig(),
                JacksonConfig.get(),
                SyncService::defaultConnectivityCheck,
                javafx.application.Platform::runLater
        );
    }

    SyncService(
            SyncApiClient apiClient,
            PendingChangesRepository pendingRepo,
            IncidentRepository incidentRepo,
            UserRepository userRepo,
            DistrictRepository districtRepo,
            StatisticsService statisticsService,
            SyncConfig syncConfig,
            ObjectMapper mapper,
            ConnectivityChecker connectivityChecker,
            UiExecutor uiExecutor
    ) {
        this.apiClient = apiClient;
        this.pendingRepo = pendingRepo;
        this.incidentRepo = incidentRepo;
        this.userRepo = userRepo;
        this.districtRepo = districtRepo;
        this.statisticsService = statisticsService;
        this.syncConfig = syncConfig;
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
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sync-cycle");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::syncCycle, 0, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /**
     * Déclenche un cycle immédiat sur le même exécuteur que les ticks — pas de
     * thread nu, et la garde {@link #running} vaut aussi pour lui.
     */
    public void syncNow() {
        if (executor != null) {
            executor.execute(this::syncCycle);
        } else {
            syncCycle();
        }
    }

    void syncCycle() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!connectivityChecker.isOnline()) {
                notifyStatus(SyncStatus.OFFLINE);
                return;
            }

            notifyStatus(SyncStatus.SYNCING);
            push();
            pull();
            statisticsService.recompute();
            notifyStatus(SyncStatus.SUCCESS);

        } catch (TokenUnavailableException e) {
            // L'access token en mémoire a expiré. Il n'y a pas de refresh
            // in-process (le cookie refresh vit dans le navigateur) : l'UI doit
            // relancer un login. Le planificateur, lui, continue de battre.
            notifyStatus(SyncStatus.AUTH_REQUIRED);
        } catch (ApiException e) {
            // Tracer le corps renvoyé par l'api : un 400 de validation n'est
            // visible que là, et sans lui le cycle échoue en boucle sans jamais
            // dire pourquoi.
            if (e.getStatusCode() != 401) {
                LOGGER.log(Level.WARNING, "Cycle de synchronisation refusé par l''api (HTTP {0}) : {1}",
                        new Object[]{e.getStatusCode(), e.getMessage()});
            }
            notifyStatus(e.getStatusCode() == 401 ? SyncStatus.AUTH_REQUIRED : SyncStatus.ERROR);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cycle de synchronisation en échec", e);
            notifyStatus(SyncStatus.ERROR);
        } finally {
            running.set(false);
        }
    }

    //  Push : H2 → Mongo

    void push() throws IOException {
        List<PendingChange> batch = pendingRepo.findBatch(PUSH_BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        Map<Long, PendingChange> byId = new HashMap<>();
        List<IngestEvent> events = new ArrayList<>();
        for (PendingChange change : batch) {
            byId.put(change.getId(), change);
            events.add(toEvent(change));
        }

        IngestResult result = apiClient.ingest(events);

        for (IngestResult.AppliedEvent applied : result.getApplied()) {
            PendingChange change = byId.get(applied.getId());
            if (change == null) {
                continue;
            }
            if (change.getMongoId() == null && applied.getMongoId() != null) {
                pendingRepo.setRecordMongoId(change.getEntity(), change.getRecordId(), applied.getMongoId());
            }
            // Le jeton de concurrence optimiste avance depuis l'ack, sans
            // attendre de revoir notre propre écriture dans le flux — c'est
            // justement pour ça que l'api renvoie l'updatedAt persisté.
            pendingRepo.advanceBaseAndClear(
                    change.getEntity(),
                    change.getRecordId(),
                    applied.getMongoId(),
                    applied.getUpdatedAt(),
                    change.getOccurredAt()
            );
        }

        for (IngestResult.ConflictAck conflict : result.getConflicts()) {
            PendingChange change = byId.get(conflict.getId());
            if (change == null) {
                continue;
            }
            // Rien n'a été écrit côté serveur : on garde la ligne en attente
            // pour ne pas perdre l'édition locale. C'est le pull qui la
            // retirera, une fois la résolution redescendue (§6.5).
            LOGGER.log(Level.INFO, "Conflit {0} sur {1}/{2}",
                    new Object[]{conflict.getConflictId(), change.getEntity(), change.getRecordId()});
        }

        List<String> rejections = new ArrayList<>();
        for (IngestResult.RejectedEvent rejected : result.getRejected()) {
            PendingChange change = byId.get(rejected.getId());
            if (change == null) {
                continue;
            }
            // Refus ferme du serveur, quelle qu'en soit la raison : le rejouer
            // ne pourra jamais aboutir. On lâche la ligne et on le dit.
            pendingRepo.delete(change.getEntity(), change.getRecordId());
            String message = describeRejection(change, rejected.getReason());
            rejections.add(message);
            LOGGER.log(Level.WARNING, message);
        }

        warnOnUnaccountedEvents(byId, result);

        pendingConflictCount = result.getConflicts().size();
        if (!result.getConflicts().isEmpty()) {
            notifyConflicts(pendingConflictCount);
        }
        if (!rejections.isEmpty()) {
            notifyRejections(rejections);
        }
    }

    private IngestEvent toEvent(PendingChange change) {
        IngestEvent event = new IngestEvent();
        event.setId(change.getId());
        event.setEntity(change.getEntity());
        event.setOperation(change.getOperation());
        event.setMongoId(change.getMongoId());
        event.setOccurredAt(SyncPayloads.toIsoUtc(change.getOccurredAt()));
        if (!PendingChange.INSERT.equals(change.getOperation())) {
            event.setBaseUpdatedAt(change.getBaseUpdatedAt());
        }
        if (change.getPayload() != null) {
            event.setData(readPayload(change));
        }
        return event;
    }

    private Map<String, Object> readPayload(PendingChange change) {
        try {
            return mapper.readValue(change.getPayload(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Charge utile illisible pour "
                    + change.getEntity() + "/" + change.getRecordId(), e);
            return null;
        }
    }

    /**
     * Contrôle de l'invariant de comptabilité totale : chaque id soumis doit
     * revenir dans <em>exactement une</em> des trois listes. Un id qui n'en
     * revient dans aucune laisserait sa ligne en attente bloquée, re-poussée à
     * chaque cycle sans jamais aboutir.
     *
     * <p>On se contente de le tracer bruyamment : c'est un bug serveur, et
     * bâtir une reprise ici masquerait le symptôme au lieu de le révéler.</p>
     */
    private void warnOnUnaccountedEvents(Map<Long, PendingChange> byId, IngestResult result) {
        Set<Long> accounted = new HashSet<>();
        result.getApplied().forEach(event -> accounted.add(event.getId()));
        result.getConflicts().forEach(event -> accounted.add(event.getId()));
        result.getRejected().forEach(event -> accounted.add(event.getId()));

        for (Map.Entry<Long, PendingChange> entry : byId.entrySet()) {
            if (!accounted.contains(entry.getKey())) {
                PendingChange change = entry.getValue();
                LOGGER.log(Level.WARNING,
                        "Événement {0} ({1}/{2}) absent de l''acquittement : "
                                + "sa ligne en attente reste bloquée (bug serveur)",
                        new Object[]{entry.getKey(), change.getEntity(), change.getRecordId()});
            }
        }
    }

    private String describeRejection(PendingChange change, String reason) {
        String label = switch (reason != null ? reason : "") {
            case "out-of-district" -> "hors de votre quartier";
            case "read-only-entity" -> "entité en lecture seule";
            case "unprocessable" -> "impossible à appliquer côté serveur";
            default -> reason;
        };
        return "Modification refusée sur " + change.getEntity() + "/" + change.getRecordId()
                + " : " + label;
    }

    //  Pull : Mongo → H2

    void pull() throws IOException {
        long cursor = syncConfig.getCursor();

        for (int page = 0; page < MAX_PULL_PAGES; page++) {
            List<ChangeEntry> entries = apiClient.changes(cursor, PULL_PAGE_SIZE);
            if (entries.isEmpty()) {
                break;
            }
            for (ChangeEntry entry : entries) {
                apply(entry);
                cursor = Math.max(cursor, entry.getIndex());
            }
            // Le curseur avance page par page : une coupure réseau en plein
            // drainage ne fait pas rejouer ce qui a déjà été appliqué.
            syncConfig.setCursor(cursor);

            if (entries.size() < PULL_PAGE_SIZE) {
                break;
            }
        }
    }

    private void apply(ChangeEntry entry) {
        SyncEntity entity = SyncEntity.fromWire(entry.getEntity());
        if (entity == null) {
            // Une entité que cette version ne connaît pas : on l'ignore mais on
            // avance quand même le curseur, sinon le flux se bloque ici.
            LOGGER.log(Level.INFO, "Entité inconnue dans le flux : {0}", entry.getEntity());
            return;
        }
        switch (entity) {
            case USER -> applyUserChange(entry);
            case INCIDENT -> applyIncidentChange(entry);
            case DISTRICT -> applyDistrictChange(entry);
        }
    }

    private void applyUserChange(ChangeEntry entry) {
        String mongoId = entry.getMongoId();
        if (PendingChange.DELETE.equals(entry.getOperation())) {
            userRepo.deleteFromSync(mongoId);
            return;
        }
        User user = SyncMapper.toUser(mongoId, entry.getData());
        String baseUpdatedAt = SyncMapper.updatedAtToken(entry.getData());
        if (userRepo.findByMongoId(mongoId).isPresent()) {
            userRepo.updateFromSync(user, mongoId, baseUpdatedAt);
        } else {
            userRepo.saveFromSync(user, mongoId, baseUpdatedAt);
        }
    }

    private void applyIncidentChange(ChangeEntry entry) {
        String mongoId = entry.getMongoId();
        if (PendingChange.DELETE.equals(entry.getOperation())) {
            incidentRepo.deleteFromSync(mongoId);
            return;
        }
        Incident incident = SyncMapper.toIncident(mongoId, entry.getData());
        String baseUpdatedAt = SyncMapper.updatedAtToken(entry.getData());
        if (incidentRepo.findByMongoId(mongoId).isPresent()) {
            incidentRepo.updateFromSync(incident, mongoId, baseUpdatedAt);
        } else {
            incidentRepo.saveFromSync(incident, mongoId, baseUpdatedAt);
        }
    }

    /**
     * Les quartiers ne descendent que du serveur (§9.5) : aucune ligne
     * {@code pending_changes} n'est jamais créée pour eux, et ils n'ont pas de
     * jeton de concurrence optimiste faute d'{@code updatedAt}.
     */
    private void applyDistrictChange(ChangeEntry entry) {
        String mongoId = entry.getMongoId();
        if (PendingChange.DELETE.equals(entry.getOperation())) {
            districtRepo.deleteFromSync(mongoId);
            return;
        }
        District district = SyncMapper.toDistrict(mongoId, entry.getData());
        if (districtRepo.findByMongoId(mongoId).isPresent()) {
            districtRepo.updateFromSync(district, mongoId);
        } else {
            districtRepo.saveFromSync(district, mongoId);
        }
    }

    //  Notifications UI

    private void notifyStatus(SyncStatus status) {
        if (statusListener != null) {
            uiExecutor.execute(() -> statusListener.onStatusChanged(status));
        }
    }

    private void notifyConflicts(int count) {
        if (conflictListener != null) {
            uiExecutor.execute(() -> conflictListener.accept(count));
        }
    }

    private void notifyRejections(List<String> messages) {
        if (rejectionListener != null) {
            uiExecutor.execute(() -> rejectionListener.accept(messages));
        }
    }

    public void setStatusListener(SyncStatusListener listener) {
        this.statusListener = listener;
    }

    /**
     * Notifié du nombre de conflits levés par le dernier push, pour afficher
     * le badge qui mène à l'écran de résolution.
     */
    public void setConflictListener(Consumer<Integer> listener) {
        this.conflictListener = listener;
    }

    /**
     * Notifié des refus d'autorisation : contrairement aux conflits, il n'y a
     * rien à résoudre — l'opérateur doit juste savoir que sa modification a
     * été abandonnée.
     */
    public void setRejectionListener(Consumer<List<String>> listener) {
        this.rejectionListener = listener;
    }

    public int getPendingConflictCount() {
        return pendingConflictCount;
    }
}
