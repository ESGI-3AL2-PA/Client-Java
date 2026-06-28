package com.connectedneighbours;

import com.connectedneighbours.auth.SsoAuthService;
import com.connectedneighbours.auth.exeption.TokenUnavailableException;
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
     * Fournit le Bearer token à l'ApiClient. Wrappe l'IOException (checked)
     * en unchecked car Supplier<String> ne déclare pas d'exception.
     */
    private String supplyAccessToken() {
        try {
            return authService.getAccessToken();
        } catch (java.io.IOException e) {
            throw new TokenUnavailableException(e);
        }
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
        return authService.isAuthenticated() || authService.hasSession();
    }

    public void logout() {
        authService.logout();
        currentUser = null;
    }
}
