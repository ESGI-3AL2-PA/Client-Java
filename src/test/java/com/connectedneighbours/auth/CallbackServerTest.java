package com.connectedneighbours.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class CallbackServerTest {

    private static final String STATE = "expected-state-value";
    private static final long TIMEOUT_MS = 3000;

    private CallbackServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new CallbackServer(STATE);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    /** Simule le navigateur arrivant sur le callback ; renvoie le status HTTP. */
    private int hitCallback(String query) throws IOException {
        HttpURLConnection conn =
                (HttpURLConnection) URI.create(server.getCallbackUrl() + "?" + query).toURL().openConnection();
        conn.setRequestMethod("GET");
        int status = conn.getResponseCode();
        conn.disconnect();
        return status;
    }

    @Test
    void bindsLoopbackOnAnEphemeralPort() {
        assertTrue(server.getPort() > 0);
        assertEquals("http://127.0.0.1:" + server.getPort() + "/callback", server.getCallbackUrl());
    }

    @Test
    void requiresAState() {
        assertThrows(IllegalArgumentException.class, () -> new CallbackServer(null));
        assertThrows(IllegalArgumentException.class, () -> new CallbackServer("  "));
    }

    @Test
    void deliversTheCodeWhenTheStateMatches() throws Exception {
        assertEquals(200, hitCallback("code=the-code&state=" + STATE));
        assertEquals("the-code", server.waitForCode(TIMEOUT_MS));
    }

    // Sans ce contrôle, n'importe quelle page ouverte sur la machine pourrait
    // appeler le callback pendant la fenêtre de login et faire consommer un code
    // qu'elle a choisi.
    @Test
    void rejectsAMismatchedState() throws Exception {
        assertEquals(400, hitCallback("code=the-code&state=forged"));
        IOException e = assertThrows(IOException.class, () -> server.waitForCode(TIMEOUT_MS));
        assertTrue(e.getMessage().contains("state"), e.getMessage());
    }

    @Test
    void rejectsAMissingState() throws Exception {
        assertEquals(400, hitCallback("code=the-code"));
        assertThrows(IOException.class, () -> server.waitForCode(TIMEOUT_MS));
    }

    @Test
    void surfacesAccessDeniedAsAReadableMessage() throws Exception {
        assertEquals(400, hitCallback("error=access_denied&state=" + STATE));
        IOException e = assertThrows(IOException.class, () -> server.waitForCode(TIMEOUT_MS));
        assertTrue(e.getMessage().contains("administrateur"), e.getMessage());
    }

    @Test
    void rejectsACallbackWithNeitherCodeNorError() throws Exception {
        assertEquals(400, hitCallback("state=" + STATE));
        assertThrows(IOException.class, () -> server.waitForCode(TIMEOUT_MS));
    }

    @Test
    void timesOutWhenTheBrowserNeverArrives() {
        assertThrows(TimeoutException.class, () -> server.waitForCode(150));
    }

    @Test
    void parseQuery_handlesEncodingEmptyAndMalformedInput() {
        assertTrue(CallbackServer.parseQuery(null).isEmpty());
        assertTrue(CallbackServer.parseQuery("").isEmpty());

        Map<String, String> params = CallbackServer.parseQuery("code=a%2Bb&state=x%20y&junk&=novalue");
        assertEquals("a+b", params.get("code"));
        assertEquals("x y", params.get("state"));
        assertFalse(params.containsKey("junk"));

        // Première occurrence retenue : un paramètre dupliqué ne peut pas écraser le premier.
        assertEquals("first", CallbackServer.parseQuery("code=first&code=second").get("code"));
    }
}
