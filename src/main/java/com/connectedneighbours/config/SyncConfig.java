package com.connectedneighbours.config;

import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * État persistant de la synchronisation, réduit à deux valeurs :
 *
 * <ul>
 *   <li>{@code instanceId} — UUID de l'installation, généré une fois puis
 *       stable. Envoyé en {@code X-Sync-Instance} : il permet au serveur de ne
 *       pas nous renvoyer nos propres écritures (echo-skip) et de nous
 *       attribuer les conflits que nos pushes ont levés.</li>
 *   <li>{@code cursor} — le dernier {@code index} du flux traité.
 *       {@code 0} est un snapshot complet, il n'y a donc pas de bootstrap
 *       séparé.</li>
 * </ul>
 *
 * <p>Pas d'URL ni de secret partagé ici : la sync passe par l'api ordinaire
 * ({@link ApiConfig}) avec le JWT de l'opérateur.</p>
 *
 * <p>Instanciable (et non statique comme {@link ApiConfig}) pour que les tests
 * puissent substituer l'état sans toucher aux préférences de la machine.</p>
 */
public class SyncConfig {

    private static final Preferences PREFS = Preferences.userNodeForPackage(SyncConfig.class);

    private static final String KEY_INSTANCE_ID = "sync.instanceId";
    private static final String KEY_CURSOR = "sync.cursor";

    public SyncConfig() {
    }

    /**
     * L'identifiant de cette installation, créé au premier appel.
     */
    public String getInstanceId() {
        String stored = PREFS.get(KEY_INSTANCE_ID, null);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        String generated = UUID.randomUUID().toString();
        PREFS.put(KEY_INSTANCE_ID, generated);
        return generated;
    }

    public long getCursor() {
        return PREFS.getLong(KEY_CURSOR, 0L);
    }

    public void setCursor(long cursor) {
        PREFS.putLong(KEY_CURSOR, Math.max(0L, cursor));
    }

    /**
     * Remet le curseur à zéro : le prochain pull redemandera le snapshot complet.
     */
    public void resetCursor() {
        PREFS.putLong(KEY_CURSOR, 0L);
    }
}
