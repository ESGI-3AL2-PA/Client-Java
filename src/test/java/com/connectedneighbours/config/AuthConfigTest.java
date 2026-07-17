package com.connectedneighbours.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthConfigTest {

    @AfterEach
    void tearDown() {
        AuthConfig.resetToDefaults();
    }

    @Test
    void defaults_returnCorrectValues() {
        assertEquals("http", AuthConfig.getScheme());
        assertEquals("localhost", AuthConfig.getHost());
        assertEquals(3001, AuthConfig.getPort());
    }

    @Test
    void jwtConstants() {
        assertEquals("auth-service", AuthConfig.JWT_ISSUER);
        assertEquals("api", AuthConfig.JWT_AUDIENCE);
        assertEquals("/.well-known/jwks.json", AuthConfig.JWKS_PATH);
    }

    @Test
    void getBaseUrl_defaults() {
        assertEquals("http://localhost:3001", AuthConfig.getBaseUrl());
    }

    @Test
    void getJwksUrl_defaults() {
        assertEquals("http://localhost:3001/.well-known/jwks.json", AuthConfig.getJwksUrl());
    }

    @Test
    void setAndGetScheme() {
        AuthConfig.setScheme("https");
        assertEquals("https", AuthConfig.getScheme());
    }

    @Test
    void setScheme_invalid_fallsBackToHttp() {
        AuthConfig.setScheme("ftp");
        assertEquals("http", AuthConfig.getScheme());
    }

    @Test
    void setAndGetHost() {
        AuthConfig.setHost("auth.example.com");
        assertEquals("auth.example.com", AuthConfig.getHost());
    }

    @Test
    void setAndGetPort() {
        AuthConfig.setPort(4000);
        assertEquals(4000, AuthConfig.getPort());
    }

    @Test
    void setPort_invalid_fallsBackToDefault() {
        AuthConfig.setPort(0);
        assertEquals(3001, AuthConfig.getPort());
        AuthConfig.setPort(99999);
        assertEquals(3001, AuthConfig.getPort());
    }

    @Test
    void getBaseUrl_withCustomSettings() {
        AuthConfig.setScheme("https");
        AuthConfig.setHost("auth.domain.com");
        AuthConfig.setPort(8443);

        assertEquals("https://auth.domain.com:8443", AuthConfig.getBaseUrl());
    }

    @Test
    void getJwksUrl_withCustomSettings() {
        AuthConfig.setScheme("http");
        AuthConfig.setHost("auth.example.com");
        AuthConfig.setPort(8080);

        assertEquals("http://auth.example.com:8080/.well-known/jwks.json", AuthConfig.getJwksUrl());
    }

    @Test
    void resetToDefaults_restoresAll() {
        AuthConfig.setScheme("https");
        AuthConfig.setHost("custom.com");
        AuthConfig.setPort(9999);
        AuthConfig.resetToDefaults();

        assertEquals("http", AuthConfig.getScheme());
        assertEquals("localhost", AuthConfig.getHost());
        assertEquals(3001, AuthConfig.getPort());
    }
}
