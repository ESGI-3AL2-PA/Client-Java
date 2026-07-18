package com.connectedneighbours.config;

import com.connectedneighbours.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionConfigTest {

    @AfterEach
    void tearDown() {
        SessionConfig.clearLastUser();
    }

    @Test
    void loadLastUser_noUserStored_returnsEmpty() {
        Optional<User> user = SessionConfig.loadLastUser();
        assertTrue(user.isEmpty());
    }

    @Test
    void saveAndLoadLastUser_roundTrips() {
        User original = new User("u1", "test@example.com", true, false,
                "John", "Doe", "1234567890", "admin", "active", 100.0);

        SessionConfig.saveLastUser(original);

        Optional<User> loaded = SessionConfig.loadLastUser();
        assertTrue(loaded.isPresent());
        assertEquals("u1", loaded.get().getId());
        assertEquals("test@example.com", loaded.get().getEmail());
        assertEquals("John", loaded.get().getFirstName());
        assertEquals("Doe", loaded.get().getLastName());
        assertEquals("admin", loaded.get().getRole());
    }

    @Test
    void saveNull_clearsLastUser() {
        User user = new User("u1", "test@example.com", true, false,
                "John", "Doe", null, "admin", "active", null);
        SessionConfig.saveLastUser(user);
        assertTrue(SessionConfig.loadLastUser().isPresent());

        SessionConfig.saveLastUser(null);
        assertTrue(SessionConfig.loadLastUser().isEmpty());
    }

    @Test
    void clearLastUser_removesStoredUser() {
        User user = new User("u1", "test@example.com", true, false,
                "John", "Doe", null, "admin", "active", null);
        SessionConfig.saveLastUser(user);
        assertTrue(SessionConfig.loadLastUser().isPresent());

        SessionConfig.clearLastUser();
        assertTrue(SessionConfig.loadLastUser().isEmpty());
    }

    @Test
    void loadLastUser_corruptedJson_returnsEmpty() {
        final String keyUser = "session.lastUser";
        final String keyLoginAt = "session.lastLoginAt";
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(SessionConfig.class);
        prefs.put(keyUser, "{invalid json}}}");
        prefs.put(keyLoginAt, "2025-07-01T12:00:00Z");

        Optional<User> user = SessionConfig.loadLastUser();
        assertTrue(user.isEmpty());
    }
}
