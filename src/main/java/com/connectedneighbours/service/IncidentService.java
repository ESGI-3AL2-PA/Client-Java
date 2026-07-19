package com.connectedneighbours.service;

import com.connectedneighbours.config.JacksonConfig;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.PendingChange;
import com.connectedneighbours.repository.IncidentRepository;
import com.connectedneighbours.repository.PendingChangesRepository;
import com.connectedneighbours.sync.SyncEntity;
import com.connectedneighbours.sync.SyncPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service métier pour la gestion des incidents.
 * Centralise la logique métier (validation, timestamps, flag synced)
 * entre les contrôleurs et le repository.
 *
 * <p>C'est ici que passent les écritures venues de l'UI : chacune inscrit une
 * ligne dans {@code pending_changes} pour que le prochain cycle la pousse.
 * Les écritures descendues du serveur, elles, empruntent les méthodes
 * {@code *FromSync} du repository et n'en créent aucune — sinon la moindre
 * lecture repartirait aussitôt en écriture.</p>
 */
public class IncidentService {

    private static final String ENTITY = SyncEntity.INCIDENT.getWire();

    private final IncidentRepository repository;
    private final PendingChangesRepository pendingRepo;
    private final ObjectMapper mapper = JacksonConfig.get();

    public IncidentService() {
        this(new IncidentRepository(), new PendingChangesRepository());
    }

    public IncidentService(IncidentRepository repository) {
        this(repository, new PendingChangesRepository());
    }

    public IncidentService(IncidentRepository repository, PendingChangesRepository pendingRepo) {
        this.repository = repository;
        this.pendingRepo = pendingRepo;
    }

    public List<Incident> getAllIncidents() {
        return repository.findAll();
    }

    public Optional<Incident> getIncidentById(String id) {
        return repository.findById(id);
    }

    public List<Incident> getIncidentsByStatus(Incident.Status status) {
        return repository.findByStatus(status);
    }

    public List<Incident> getIncidentsByStatus(String statusValue) {
        return repository.findByStatus(statusValue);
    }

    /**
     * Retourne la liste des catégories distinctes présentes en BDD.
     * Utile pour alimenter le filtre ComboBox.
     */
    public List<String> getDistinctCategories() {
        return repository.findAll().stream()
                .map(Incident::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public int countByStatus(Incident.Status status) {
        return repository.countByStatus(status.getValue());
    }

    public int countUnsynced() {
        return repository.countUnsynced();
    }

    /**
     * Crée un nouvel incident avec les champs obligatoires.
     *
     * @param category    Catégorie (non vide)
     * @param description Description (non vide)
     * @param reporterId  ID du signaleur (user courant)
     * @return L'incident créé
     * @throws IllegalArgumentException si catégorie ou description est vide
     */
    public Incident createIncident(String category, String description, String reporterId) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("La catégorie est obligatoire.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("La description est obligatoire.");
        }

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID().toString());
        incident.setCategory(category.trim());
        incident.setDescription(description.trim());
        incident.setReporterId(reporterId != null ? reporterId : "unknown");
        incident.setDistrictId("");
        incident.setStatus(Incident.Status.OPEN.getValue());
        incident.setSynced(false);
        incident.setCreatedAt(LocalDateTime.now());
        incident.setUpdatedAt(LocalDateTime.now());

        repository.save(incident);
        // Jamais vu du serveur : ni mongo_id ni jeton de concurrence.
        pendingRepo.upsert(ENTITY, incident.getId(), PendingChange.INSERT,
                null, payloadOf(incident), null);
        return incident;
    }

    /**
     * Met à jour un incident existant. Force la mise à jour de
     * {@code updatedAt} et positionne {@code synced = false}.
     *
     * @param incident L'incident modifié
     * @throws IllegalArgumentException si l'incident est null ou sans ID
     */
    public void updateIncident(Incident incident) {
        if (incident == null || incident.getId() == null) {
            throw new IllegalArgumentException("Incident invalide.");
        }
        incident.setUpdatedAt(LocalDateTime.now());
        incident.setSynced(false);
        repository.update(incident);

        PendingChangesRepository.RecordSyncState state =
                pendingRepo.findRecordSyncState(ENTITY, incident.getId());
        // Sans mongo_id, le serveur n'a jamais vu cet incident : c'est encore
        // une création, pas une mise à jour.
        String operation = state.mongoId() == null ? PendingChange.INSERT : PendingChange.UPDATE;
        pendingRepo.upsert(ENTITY, incident.getId(), operation,
                state.mongoId(), payloadOf(incident), state.baseUpdatedAt());
    }

    /**
     * Supprime un incident par son ID.
     *
     * @param id Identifiant de l'incident
     */
    public void deleteIncident(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("L'ID de l'incident est obligatoire.");
        }
        // Lu avant la suppression : ensuite la ligne n'est plus là pour le dire.
        PendingChangesRepository.RecordSyncState state = pendingRepo.findRecordSyncState(ENTITY, id);
        repository.delete(id);

        // Si l'incident n'a jamais atteint le serveur, la collapse du
        // repository annulera purement et simplement la création en attente.
        pendingRepo.upsert(ENTITY, id, PendingChange.DELETE,
                state.mongoId(), null, state.baseUpdatedAt());
    }

    private String payloadOf(Incident incident) {
        try {
            Map<String, Object> data = SyncPayloads.forIncident(incident);
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(IncidentService.class.getName()).log(
                    java.util.logging.Level.WARNING,
                    "Impossible de sérialiser l'incident " + incident.getId(),
                    e
            );
            return null;
        }
    }
}
