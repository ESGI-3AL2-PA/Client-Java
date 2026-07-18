package com.connectedneighbours.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiConfigTest {

    @AfterEach
    void tearDown() {
        ApiConfig.resetToDefaults();
    }

    @Test
    void defaults_returnCorrectValues() {
        assertEquals("http", ApiConfig.getScheme());
        assertEquals("localhost", ApiConfig.getHost());
        assertEquals(3000, ApiConfig.getPort());
    }

    @Test
    void getBaseUrl_defaults() {
        assertEquals("http://localhost:3000", ApiConfig.getBaseUrl());
    }

    @Test
    void setAndGetScheme() {
        ApiConfig.setScheme("https");
        assertEquals("https", ApiConfig.getScheme());
    }

    @Test
    void setScheme_invalidValue_fallsBackToHttp() {
        ApiConfig.setScheme("ftp");
        assertEquals("http", ApiConfig.getScheme());
    }

    @Test
    void setScheme_nullOrBlank_usesDefault() {
        ApiConfig.setScheme(null);
        assertEquals("http", ApiConfig.getScheme());
        ApiConfig.setScheme("  ");
        assertEquals("http", ApiConfig.getScheme());
    }

    @Test
    void setAndGetHost() {
        ApiConfig.setHost("api.example.com");
        assertEquals("api.example.com", ApiConfig.getHost());
    }

    @Test
    void setHost_ipv6Brackets_stripsThem() {
        ApiConfig.setHost("[::1]");
        assertEquals("::1", ApiConfig.getHost());
    }

    @Test
    void setHost_nullOrBlank_usesDefault() {
        ApiConfig.setHost(null);
        assertEquals("localhost", ApiConfig.getHost());
        ApiConfig.setHost("  ");
        assertEquals("localhost", ApiConfig.getHost());
    }

    @Test
    void setAndGetPort() {
        ApiConfig.setPort(8080);
        assertEquals(8080, ApiConfig.getPort());
    }

    @Test
    void setPort_invalid_fallsBackToDefault() {
        ApiConfig.setPort(0);
        assertEquals(3000, ApiConfig.getPort());
        ApiConfig.setPort(70000);
        assertEquals(3000, ApiConfig.getPort());
    }

    @Test
    void setPort_null_clearsPort() {
        ApiConfig.setPort((String) null);
        assertEquals(-1, ApiConfig.getPort());
    }

    @Test
    void setPort_empty_clearsPort() {
        ApiConfig.setPort("");
        assertEquals(-1, ApiConfig.getPort());
    }

    @Test
    void setPort_invalidString_fallsBackToDefault() {
        ApiConfig.setPort("abc");
        assertEquals(3000, ApiConfig.getPort());
    }

    @Test
    void getBaseUrl_withCustomHostAndPort() {
        ApiConfig.setHost("192.168.1.1");
        ApiConfig.setPort(9090);

        assertEquals("http://192.168.1.1:9090", ApiConfig.getBaseUrl());
    }

    @Test
    void getBaseUrl_withEmptyPort_omitsPort() {
        ApiConfig.setPort("");

        assertEquals("http://localhost", ApiConfig.getBaseUrl());
    }

    @Test
    void getBaseUrl_ipv6Host_wrapsInBrackets() {
        ApiConfig.setHost("::1");
        ApiConfig.setPort(3000);

        assertEquals("http://[::1]:3000", ApiConfig.getBaseUrl());
    }

    @Test
    void getBaseUrl_ipv6WithBracketsAlready_keepsThem() {
        ApiConfig.setHost("[::1]");

        assertEquals("http://[::1]:3000", ApiConfig.getBaseUrl());
    }

    @Test
    void getPortForSocket_default() {
        assertEquals(3000, ApiConfig.getPortForSocket());
    }

    @Test
    void getPortForSocket_emptyPort_returns80ForHttp() {
        ApiConfig.setPort("");
        assertEquals(80, ApiConfig.getPortForSocket());
    }

    @Test
    void getPortForSocket_emptyPort_returns443ForHttps() {
        ApiConfig.setScheme("https");
        ApiConfig.setPort("");
        assertEquals(443, ApiConfig.getPortForSocket());
    }

    @Test
    void getPortText_defaultsToDefaultPort() {
        assertEquals("3000", ApiConfig.getPortText());
    }

    @Test
    void getPortText_afterClearing_returnsEmpty() {
        ApiConfig.setPort("");
        assertEquals("", ApiConfig.getPortText());
    }

    @Test
    void resetToDefaults_restoresAll() {
        ApiConfig.setScheme("https");
        ApiConfig.setHost("custom.com");
        ApiConfig.setPort(9999);
        ApiConfig.resetToDefaults();

        assertEquals("http", ApiConfig.getScheme());
        assertEquals("localhost", ApiConfig.getHost());
        assertEquals(3000, ApiConfig.getPort());
        assertEquals("http://localhost:3000", ApiConfig.getBaseUrl());
    }
}
