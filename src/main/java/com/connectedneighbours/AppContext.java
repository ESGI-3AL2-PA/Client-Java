package com.connectedneighbours;

import com.connectedneighbours.auth.SsoAuthService;
import com.connectedneighbours.auth.exception.TokenUnavailableException;
import com.connectedneighbours.config.AuthConfig;
import com.connectedneighbours.config.SessionConfig;
import com.connectedneighbours.model.District;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.repository.SyncApiClient;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import java.util.Optional;

/**
 * Contexte de l'app partagé : (SsoAuthService, ApiClient,
 * user courant). Injecté dans les contrôleurs JavaFX à la place d'un câblage
 * ad-hoc dans MainApp.
 */
public class AppContext {

    private final SsoAuthService authService;
    private final ApiClient apiClient;
    private final SyncApiClient syncApiClient;
    private volatile User currentUser;

    /**
     * Quartier consulté. Observable : les écrans sont reconstruits à chaque
     * navigation alors que ce contexte survit, donc un contrôleur s'abonne
     * plutôt que d'être notifié via un registre de callbacks.
     * <p>
     * Filtre d'affichage uniquement — le serveur reste seul juge de ce qui est
     * lisible et écrivable (rejets {@code out-of-district} côté sync).
     */
    private final ObjectProperty<String> activeDistrictId = new SimpleObjectProperty<>(this, "activeDistrictId");
    private volatile List<District> availableDistricts = List.of();

    public AppContext() {
        this.authService = new SsoAuthService();
        this.apiClient = new ApiClient(this::supplyAccessToken);
        // Même token que l'ApiClient : les routes de sync sont des routes api
        // ordinaires, authentifiées par le JWT de l'opérateur.
        this.syncApiClient = new SyncApiClient(this::supplyAccessToken);
    }

    /**
     * Fournit le Bearer token à l'ApiClient. Lève {@link
     * TokenUnavailableException} si le token est expiré — l'UI doit alors
     * relancer le login navigateur.
     */
    private String supplyAccessToken() {
        return authService.getAccessToken();
    }

    public SsoAuthService getAuthService() {
        return authService;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public SyncApiClient getSyncApiClient() {
        return syncApiClient;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean isAuthenticated() {
        return authService.isAuthenticated();
    }

    //  Périmètre quartier

    public ObjectProperty<String> activeDistrictIdProperty() {
        return activeDistrictId;
    }

    public String getActiveDistrictId() {
        return activeDistrictId.get();
    }

    /**
     * Change le quartier consulté. Persisté uniquement pour un superAdmin : pour
     * un admin la valeur est imposée par son rôle, la retenir n'aurait aucun sens.
     */
    public void setActiveDistrictId(String districtId) {
        activeDistrictId.set(districtId);
        if (canSwitchDistrict()) {
            SessionConfig.saveActiveDistrictId(districtId);
        }
    }

    public List<District> getAvailableDistricts() {
        return availableDistricts;
    }

    /** Seul un superAdmin reçoit le flux multi-quartiers et peut donc arbitrer. */
    public boolean canSwitchDistrict() {
        return currentUser != null && AuthConfig.isSuperAdminRole(currentUser.getRole());
    }

    /**
     * Fixe le périmètre après login.
     * <p>
     * superAdmin : reprend le dernier quartier choisi s'il existe toujours, sinon
     * aucun filtre — mieux vaut tout montrer que masquer d'emblée les
     * enregistrements sans quartier. Admin : le quartier administré, jamais celui
     * de résidence — un admin peut habiter ailleurs que là où il modère.
     *
     * @param districts quartiers connus localement (H2), donc disponibles hors ligne
     */
    public void initDistrictScope(List<District> districts) {
        availableDistricts = districts == null ? List.of() : List.copyOf(districts);

        // La liste reste peuplée pour un admin : elle ne contient de toute façon que
        // son propre quartier (le serveur scope le flux de sync) et sert à résoudre
        // le nom affiché dans le badge.
        if (!canSwitchDistrict()) {
            activeDistrictId.set(currentUser != null ? currentUser.getAdminDistrictId() : null);
            return;
        }

        Optional<String> remembered = SessionConfig.loadActiveDistrictId()
                .filter(id -> availableDistricts.stream().anyMatch(d -> id.equals(d.getId())));
        setActiveDistrictId(remembered.orElse(null));
    }

    /** Nom du quartier actif, pour l'affichage. */
    public Optional<String> getActiveDistrictName() {
        String id = activeDistrictId.get();
        if (id == null) return Optional.empty();
        return availableDistricts.stream()
                .filter(d -> id.equals(d.getId()))
                .map(District::getName)
                .findFirst();
    }

    /**
     * Déconnexion locale : efface le token et le user côté Java. Le cookie
     * refresh reste dans le navigateur (≤7j). Efface également le dernier utilisateur mémorisé ({@link SessionConfig}) :
     * le prochain démarrage exigera donc un re-login (skip du SSO désactivé).
     */
    public void logout() {
        SessionConfig.clearLastUser();
        // Le quartier retenu appartient au compte qui part : le conserver le
        // ferait fuiter dans la session suivante, qui peut être un autre opérateur.
        SessionConfig.clearActiveDistrictId();
        authService.logout();
        currentUser = null;
        activeDistrictId.set(null);
        availableDistricts = List.of();
    }
}
