package com.connectedneighbours.service;

import com.connectedneighbours.repository.SyncApiClient;
import com.connectedneighbours.sync.Conflict;
import com.connectedneighbours.sync.ResolveConflictRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Les conflits de synchronisation et leur résolution.
 *
 * <p>Le client lourd est la <em>seule</em> surface de résolution (§6.5) : il
 * n'y a pas d'écran côté web. L'opérateur ne voit que les conflits levés par
 * <em>ses propres</em> pushes, filtrés serveur-side sur
 * {@code X-Sync-Instance}.</p>
 *
 * <p>Après résolution, on ne touche pas à la ligne {@code pending_changes} :
 * l'état résolu redescend par le prochain {@code GET /changes} et c'est
 * l'application de ce changement qui la retire. Un seul chemin réconcilie
 * H2.</p>
 */
public class ConflictService {

    private static final int DEFAULT_LIMIT = 100;

    private final SyncApiClient apiClient;

    public ConflictService(SyncApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Les conflits en attente levés par cette installation.
     */
    public List<Conflict> findMine() throws IOException {
        return apiClient.myConflicts(DEFAULT_LIMIT);
    }

    /**
     * Applique le snapshot local capturé au moment du push.
     */
    public void resolveWithLocal(String conflictId) throws IOException {
        apiClient.resolveConflict(conflictId, ResolveConflictRequest.local());
    }

    /**
     * Garde la version serveur, qui sera re-propagée aux autres instances.
     */
    public void resolveWithServer(String conflictId) throws IOException {
        apiClient.resolveConflict(conflictId, ResolveConflictRequest.server());
    }

    /**
     * Applique la fusion champ à champ produite par l'opérateur.
     */
    public void resolveWithMerge(String conflictId, Map<String, Object> merged) throws IOException {
        apiClient.resolveConflict(conflictId, ResolveConflictRequest.merged(merged));
    }
}
