package com.connectedneighbours;

import com.connectedneighbours.config.SessionConfig;
import com.connectedneighbours.model.District;
import com.connectedneighbours.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    //  Périmètre quartier
    //
    // Le stockage réel (SessionConfig, backed par java.util.prefs) est utilisé tel
    // quel et nettoyé entre les tests : c'est lui qui porte la persistance d'une
    // session à l'autre, le mocker ne testerait rien.

    private static District district(String id, String name) {
        District d = new District();
        d.setId(id);
        d.setName(name);
        return d;
    }

    private static User scopedUser(String role, String adminDistrictId) {
        User u = new User();
        u.setRole(role);
        u.setAdminDistrictId(adminDistrictId);
        return u;
    }

    @BeforeEach
    void clearRememberedDistrict() {
        SessionConfig.clearActiveDistrictId();
    }

    @AfterEach
    void clearRememberedDistrictAfter() {
        SessionConfig.clearActiveDistrictId();
    }

    @Test
    void canSwitchDistrict_onlyForSuperAdmin() {
        AppContext context = new AppContext();
        assertFalse(context.canSwitchDistrict());

        context.setCurrentUser(scopedUser("admin", "d1"));
        assertFalse(context.canSwitchDistrict());

        context.setCurrentUser(scopedUser("superAdmin", null));
        assertTrue(context.canSwitchDistrict());
    }

    /**
     * Pas de filtre par défaut : un enregistrement sans quartier, ou rattaché à un
     * quartier absent localement, resterait sinon invisible sous toute sélection.
     */
    @Test
    void initDistrictScope_superAdmin_defaultsToNoFilter() {
        AppContext context = new AppContext();
        context.setCurrentUser(scopedUser("superAdmin", null));
        context.initDistrictScope(List.of(district("d1", "Centre"), district("d2", "Nord")));

        assertNull(context.getActiveDistrictId());
        assertTrue(context.getActiveDistrictName().isEmpty());
    }

    @Test
    void initDistrictScope_superAdmin_restoresRememberedDistrict() {
        SessionConfig.saveActiveDistrictId("d2");

        AppContext context = new AppContext();
        context.setCurrentUser(scopedUser("superAdmin", null));
        context.initDistrictScope(List.of(district("d1", "Centre"), district("d2", "Nord")));

        assertEquals("d2", context.getActiveDistrictId());
    }

    /** Un quartier supprimé entre deux sessions ne doit pas laisser un scope fantôme. */
    @Test
    void initDistrictScope_superAdmin_ignoresRememberedDistrictThatNoLongerExists() {
        SessionConfig.saveActiveDistrictId("gone");

        AppContext context = new AppContext();
        context.setCurrentUser(scopedUser("superAdmin", null));
        context.initDistrictScope(List.of(district("d1", "Centre")));

        assertNull(context.getActiveDistrictId());
    }

    @Test
    void initDistrictScope_superAdmin_withNoDistrictsLeavesScopeNull() {
        AppContext context = new AppContext();
        context.setCurrentUser(scopedUser("superAdmin", null));
        context.initDistrictScope(List.of());

        assertNull(context.getActiveDistrictId());
    }

    /**
     * Le quartier administré, pas celui de résidence : un admin peut habiter
     * ailleurs que là où il modère.
     */
    @Test
    void initDistrictScope_admin_boundToAdminDistrictNotResidence() {
        User admin = scopedUser("admin", "d2");
        admin.setDistrictId("d1");

        AppContext context = new AppContext();
        context.setCurrentUser(admin);
        context.initDistrictScope(List.of(district("d1", "Centre"), district("d2", "Nord")));

        assertEquals("d2", context.getActiveDistrictId());
        assertEquals("Nord", context.getActiveDistrictName().orElseThrow());
    }

    @Test
    void initDistrictScope_admin_doesNotPersistScope() {
        AppContext context = new AppContext();
        context.setCurrentUser(scopedUser("admin", "d2"));
        context.initDistrictScope(List.of(district("d2", "Nord")));

        assertTrue(SessionConfig.loadActiveDistrictId().isEmpty());
    }

    @Test
    void logout_clearsScopeSoItCannotLeakIntoTheNextSession() {
        AppContext context = new AppContext();
        context.setCurrentUser(scopedUser("superAdmin", null));
        context.initDistrictScope(List.of(district("d1", "Centre")));
        context.setActiveDistrictId("d1");
        assertEquals("d1", context.getActiveDistrictId());

        context.logout();

        assertNull(context.getActiveDistrictId());
        assertTrue(context.getAvailableDistricts().isEmpty());
        assertTrue(SessionConfig.loadActiveDistrictId().isEmpty());
    }
}
