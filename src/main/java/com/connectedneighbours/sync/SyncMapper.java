package com.connectedneighbours.sync;

import com.connectedneighbours.model.District;
import com.connectedneighbours.model.Incident;
import com.connectedneighbours.model.User;

import java.util.Map;

/**
 * Transforme le {@code data} d'une entrée du flux en modèle local.
 *
 * <p>Le mapping est écrit à la main plutôt que délégué à Jackson : les dates
 * du serveur sont en ISO 8601 UTC (suffixe {@code Z}), que le désérialiseur
 * {@code LocalDateTime} par défaut rejette — même raison que dans
 * {@code SsoAuthService}.</p>
 *
 * <p>L'identifiant local des enregistrements venus du serveur est
 * l'identifiant Mongo lui-même : les deux côtés utilisent des UUID en chaîne
 * (§8), il n'y a donc rien à inventer.</p>
 */
public final class SyncMapper {

    private SyncMapper() {
    }

    public static User toUser(String mongoId, Map<String, Object> data) {
        User user = new User();
        user.setId(mongoId);
        user.setEmail(text(data, "email"));
        user.setFirstName(text(data, "firstName"));
        user.setLastName(text(data, "lastName"));
        user.setPhone(text(data, "phone"));
        user.setAddress(text(data, "address"));
        user.setRole(text(data, "role"));
        user.setStatus(text(data, "status"));
        user.setDistrictId(text(data, "districtId"));
        user.setEmailVerified(bool(data, "emailVerified"));
        user.setTotpEnabled(bool(data, "totpEnabled"));
        user.setBalance(number(data, "balance"));
        user.setCreatedAt(SyncPayloads.fromIsoUtc(text(data, "createdAt")));
        user.setUpdatedAt(SyncPayloads.fromIsoUtc(text(data, "updatedAt")));
        return user;
    }

    public static Incident toIncident(String mongoId, Map<String, Object> data) {
        Incident incident = new Incident();
        incident.setId(mongoId);
        incident.setReporterId(text(data, "reporterId"));
        incident.setDistrictId(text(data, "districtId"));
        incident.setCategory(text(data, "category"));
        incident.setDescription(text(data, "description"));
        incident.setPhotoUrl(text(data, "photoUrl"));
        incident.setStatus(text(data, "status"));
        incident.setAssignedTo(text(data, "assignedTo"));
        incident.setCreatedAt(SyncPayloads.fromIsoUtc(text(data, "createdAt")));
        incident.setUpdatedAt(SyncPayloads.fromIsoUtc(text(data, "updatedAt")));
        incident.setSynced(true);
        return incident;
    }

    public static District toDistrict(String mongoId, Map<String, Object> data) {
        District district = new District();
        district.setId(mongoId);
        district.setName(text(data, "name"));
        return district;
    }

    /**
     * Le jeton de concurrence optimiste, gardé tel quel : c'est la chaîne
     * exacte que le serveur comparera au prochain UPDATE.
     */
    public static String updatedAtToken(Map<String, Object> data) {
        return text(data, "updatedAt");
    }

    private static String text(Map<String, Object> data, String field) {
        if (data == null) {
            return null;
        }
        Object value = data.get(field);
        return value != null ? String.valueOf(value) : null;
    }

    private static Boolean bool(Map<String, Object> data, String field) {
        if (data == null) {
            return null;
        }
        Object value = data.get(field);
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null ? Boolean.valueOf(String.valueOf(value)) : null;
    }

    private static Double number(Map<String, Object> data, String field) {
        if (data == null) {
            return null;
        }
        Object value = data.get(field);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
