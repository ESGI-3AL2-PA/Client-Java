package com.connectedneighbours;

import com.connectedneighbours.auth.SsoAuthService;
import com.connectedneighbours.auth.exception.TokenUnavailableException;
import com.connectedneighbours.model.User;
import com.connectedneighbours.repository.ApiClient;

/**
 * Contexte de l'app partagé : (SsoAuthService, ApiClient,
 * user courant). Injecté dans les contrôleurs JavaFX à la place d'un câblage
 * ad-hoc dans MainApp.
 */
public class AppContext {

    private final SsoAuthService authService;
    private final ApiClient apiClient;
    private volatile User currentUser;

    public AppContext() {
        this.authService = new SsoAuthService();
        this.apiClient = new ApiClient(this::supplyAccessToken);
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
     * refresh reste dans le navigateur (≤7j).
     */
    public void logout() {
        authService.logout();
        currentUser = null;
    }
}
