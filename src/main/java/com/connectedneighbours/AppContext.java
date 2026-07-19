package com.connectedneighbours;

import com.connectedneighbours.auth.SsoAuthService;
import com.connectedneighbours.auth.exception.TokenUnavailableException;
import com.connectedneighbours.config.SessionConfig;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.ApiClient;
import com.connectedneighbours.repository.SyncApiClient;

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

    /**
     * Déconnexion locale : efface le token et le user côté Java. Le cookie
     * refresh reste dans le navigateur (≤7j). Efface également le dernier utilisateur mémorisé ({@link SessionConfig}) :
     * le prochain démarrage exigera donc un re-login (skip du SSO désactivé).
     */
    public void logout() {
        SessionConfig.clearLastUser();
        authService.logout();
        currentUser = null;
    }
}
