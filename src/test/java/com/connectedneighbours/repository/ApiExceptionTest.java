package com.connectedneighbours.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionTest {

    @Test
    void constructor_storesStatusCodeAndMessage() {
        ApiException ex = new ApiException(404, "Not Found");

        assertEquals(404, ex.getStatusCode());
        assertEquals("Not Found", ex.getMessage());
    }

    @Test
    void isNotFound_returnsTrueFor404() {
        ApiException ex = new ApiException(404, "Not Found");
        assertTrue(ex.isNotFound());
    }

    @Test
    void isNotFound_returnsFalseForOtherCodes() {
        assertFalse(new ApiException(400, "Bad Request").isNotFound());
        assertFalse(new ApiException(500, "Server Error").isNotFound());
        assertFalse(new ApiException(403, "Forbidden").isNotFound());
        assertFalse(new ApiException(200, "OK").isNotFound());
    }

    @Test
    void isIOExceptionSubclass() {
        ApiException ex = new ApiException(500, "Error");
        assertTrue(ex instanceof java.io.IOException);
    }
}
