package com.connectedneighbours.config;

import com.connectedneighbours.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Persistance du dernier utilisateur connecté, pour permettre un
 * démarrage offline-first : si un user est mémorisé ET que la base H2
 * locale contient des données, skip le login SSO et ouvre directement le dashboard.
 * <p>
 * Stockage via {@link java.util.prefs.Preferences} (pattern identique
 * à {@link ApiConfig} et {@link AuthConfig}. Le {@link User}
 * est sérialisé en JSON via {@link JacksonConfig} (qui gère
 * {@link java.time.LocalDateTime} avec jsr310).</p>
 * <p>
 * Aucun access token n'est persisté : le refresh token reste dans le navigateur).
 * L'app démarrera donc en mode hors-ligne tant que l'utilisateur ne relance pas un login navigateur.</p>
 */
public class SessionConfig {

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(SessionConfig.class).node(BuildConfig.profile());
    private static final ObjectMapper MAPPER = JacksonConfig.get();

    private static final String KEY_LAST_USER = "session.lastUser";
    private static final String KEY_LAST_LOGIN_AT = "session.lastLoginAt";
    private static final String KEY_ACTIVE_DISTRICT = "district.activeId";

    private SessionConfig() {
    }

    /**
     * Mémorise l'utilisateur donné (sérialisé en JSON) ainsi que le
     * timestamp du moment de la connexion. Appelé après un login SSO réussi.
     */
    public static void saveLastUser(User user) {
        if (user == null) {
            clearLastUser();
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(user);
            PREFS.put(KEY_LAST_USER, json);
            PREFS.put(KEY_LAST_LOGIN_AT, Instant.now().toString());
        } catch (Exception e) {
            // Ne jamais faire planter le login à cause d'un souci de persistance.
            java.util.logging.Logger.getLogger(SessionConfig.class.getName()).log(
                    java.util.logging.Level.WARNING,
                    "Impossible de mémoriser le dernier utilisateur",
                    e
            );
        }
    }

    /**
     * Recharge le dernier utilisateur mémorisé, ou {@code Optional.empty()}
     * si aucun n'est stocké ou si le JSON est corrompu.
     */
    public static Optional<User> loadLastUser() {
        String json = PREFS.get(KEY_LAST_USER, null);
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(json, User.class));
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(SessionConfig.class.getName()).log(
                    java.util.logging.Level.WARNING,
                    "Dernier utilisateur illisible, on l'ignore",
                    e
            );
            return Optional.empty();
        }
    }

    /**
     * Efface le dernier utilisateur mémorisé. Appelé à la déconnexion
     * pour que le prochain démarrage exige un re-login.
     */
    public static void clearLastUser() {
        PREFS.remove(KEY_LAST_USER);
        PREFS.remove(KEY_LAST_LOGIN_AT);
    }

    /**
     * Quartier sélectionné par un superAdmin, retenu d'une session à l'autre —
     * équivalent du {@code localStorage["adminDistrictScope"]} de l'admin-front.
     * Sans effet pour un admin, dont le quartier est imposé par son rôle.
     */
    public static void saveActiveDistrictId(String districtId) {
        if (districtId == null || districtId.isBlank()) {
            PREFS.remove(KEY_ACTIVE_DISTRICT);
        } else {
            PREFS.put(KEY_ACTIVE_DISTRICT, districtId);
        }
    }

    public static Optional<String> loadActiveDistrictId() {
        String id = PREFS.get(KEY_ACTIVE_DISTRICT, null);
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    public static void clearActiveDistrictId() {
        PREFS.remove(KEY_ACTIVE_DISTRICT);
    }
}
