package com.connectedneighbours.sync;

import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Construit les snapshots poussés à {@code POST /ingest}.
 *
 * <p>Seuls les champs que le serveur accepte d'une source H2 sont envoyés
 * (§5.3) : {@code role}, {@code balance}, {@code banned}, les secrets… restent
 * la propriété du serveur. Le serveur ré-applique de toute façon sa propre
 * liste blanche — envoyer moins évite simplement du bruit et rend explicite ce
 * que le client prétend posséder.</p>
 */
public final class SyncPayloads {

    private SyncPayloads() {
    }

    public static Map<String, Object> forUser(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("email", user.getEmail());
        data.put("firstName", user.getFirstName());
        data.put("lastName", user.getLastName());
        data.put("phone", user.getPhone());
        data.put("address", user.getAddress());
        data.put("districtId", user.getDistrictId());
        return data;
    }

    public static Map<String, Object> forIncident(Incident incident) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reporterId", incident.getReporterId());
        data.put("districtId", incident.getDistrictId());
        data.put("category", incident.getCategory());
        data.put("description", incident.getDescription());
        data.put("photoUrl", incident.getPhotoUrl());
        data.put("status", incident.getStatus());
        data.put("assignedTo", incident.getAssignedTo());
        if (incident.getHistory() != null) {
            data.put("history", incident.getHistory());
        }
        return data;
    }

    /**
     * Horodatage ISO 8601 en UTC, le format attendu par l'api pour
     * {@code occurredAt}.
     */
    public static String toIsoUtc(LocalDateTime moment) {
        LocalDateTime value = moment != null ? moment : LocalDateTime.now();
        return value.atZone(ZoneId.systemDefault()).toInstant().toString();
    }

    /**
     * Inverse de {@link #toIsoUtc} : les dates du flux arrivent avec un
     * suffixe {@code Z} que le désérialiseur {@code LocalDateTime} par défaut
     * ne sait pas lire.
     */
    public static LocalDateTime fromIsoUtc(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }
}
