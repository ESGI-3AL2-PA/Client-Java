package com.connectedneighbours;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppContextTest {

    @Test
    void constructor_initializesAuthServiceAndApiClient() {
        AppContext context = new AppContext();

        assertNotNull(context.getAuthService());
        assertNotNull(context.getApiClient());
    }

    @Test
    void currentUser_defaultsToNull() {
        AppContext context = new AppContext();
        assertNull(context.getCurrentUser());
    }

    @Test
    void setAndGetCurrentUser() {
        AppContext context = new AppContext();
        com.connectedneighbours.model.User user = new com.connectedneighbours.model.User(
                "u1", "test@example.com", true, false,
                "John", "Doe", null, "admin", "active", null);

        context.setCurrentUser(user);
        assertSame(user, context.getCurrentUser());
    }

    @Test
    void isAuthenticated_returnsFalseByDefault() {
        AppContext context = new AppContext();
        assertFalse(context.isAuthenticated());
    }

    @Test
    void logout_setsCurrentUserToNull() {
        AppContext context = new AppContext();
        com.connectedneighbours.model.User user = new com.connectedneighbours.model.User(
                "u1", "test@example.com", true, false,
                "John", "Doe", null, "admin", "active", null);
        context.setCurrentUser(user);
        assertNotNull(context.getCurrentUser());

        context.logout();
        assertNull(context.getCurrentUser());
    }
}
