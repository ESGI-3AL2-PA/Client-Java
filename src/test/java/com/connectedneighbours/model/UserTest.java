package com.connectedneighbours.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void defaultConstructor_createsEmptyUser() {
        User user = new User();
        assertNull(user.getId());
        assertNull(user.getEmail());
        assertNull(user.getRole());
    }

    @Test
    void parameterizedConstructor_setsAllFields() {
        User user = new User("u1", "test@example.com", true, false, "John", "Doe",
                "1234567890", "admin", "active", 100.0);

        assertEquals("u1", user.getId());
        assertEquals("test@example.com", user.getEmail());
        assertTrue(user.isEmailVerified());
        assertFalse(user.getTotpEnabled());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals("1234567890", user.getPhone());
        assertEquals("admin", user.getRole());
        assertEquals("active", user.getStatus());
        assertEquals(100.0, user.getBalance());
    }

    @Test
    void gettersAndSetters_workCorrectly() {
        User user = new User();
        LocalDateTime now = LocalDateTime.now();

        user.setId("u1");
        user.setEmail("a@b.com");
        user.setEmailVerified(true);
        user.setTotpEnabled(true);
        user.setFirstName("Jane");
        user.setLastName("Smith");
        user.setPhone("0999999999");
        user.setRole("citizen");
        user.setStatus("inactive");
        user.setBalance(50.0);
        user.setAddress("123 Main St");
        user.setDistrictId("d1");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        assertEquals("u1", user.getId());
        assertEquals("a@b.com", user.getEmail());
        assertTrue(user.isEmailVerified());
        assertTrue(user.getTotpEnabled());
        assertEquals("Jane", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertEquals("0999999999", user.getPhone());
        assertEquals("citizen", user.getRole());
        assertEquals("inactive", user.getStatus());
        assertEquals(50.0, user.getBalance());
        assertEquals("123 Main St", user.getAddress());
        assertEquals("d1", user.getDistrictId());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getUpdatedAt());
    }

    @Test
    void toString_containsKeyFields() {
        User user = new User("u1", "test@example.com", true, false, "John", "Doe",
                "1234567890", "admin", "active", 100.0);
        String str = user.toString();
        assertTrue(str.contains("u1"));
        assertTrue(str.contains("test@example.com"));
        assertTrue(str.contains("John"));
        assertTrue(str.contains("admin"));
    }
}
