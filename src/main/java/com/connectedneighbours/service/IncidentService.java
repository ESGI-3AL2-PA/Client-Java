package com.connectedneighbours.service;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.repository.IncidentRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service métier pour la gestion des incidents.
 * Centralise la logique métier (validation, timestamps, flag synced)
 * entre les contrôleurs et le repository.
 */
public class IncidentService {

    private final IncidentRepository repository;

    public IncidentService() {
        this.repository = new IncidentRepository();
    }

    public IncidentService(IncidentRepository repository) {
        this.repository = repository;
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
        repository.delete(id);
    }
}
