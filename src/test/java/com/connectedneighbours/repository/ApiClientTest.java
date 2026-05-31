package com.connectedneighbours.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiClientTest {

    private ApiClient apiClient;

    @BeforeEach
    void setUp() {
        apiClient = new ApiClient();
        apiClient.setToken("fake-token");
    }

    @Test
    void testGetCall() {
        assertThrows(IOException.class, () -> apiClient.get("/test"));
    }

    @Test
    void testPostCall() {
        assertThrows(IOException.class, () -> apiClient.post("/test", "body test"));
    }

    @Test
    void testPutCall() {
        assertThrows(IOException.class, () -> apiClient.put("/test", "body test"));
    }

    @Test
    void testDeleteCall() {
        assertThrows(IOException.class, () -> apiClient.delete("/test"));
    }
}
