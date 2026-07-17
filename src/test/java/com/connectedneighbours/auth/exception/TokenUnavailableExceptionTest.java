package com.connectedneighbours.auth.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TokenUnavailableExceptionTest {

    @Test
    void constructor_wrapsCause() {
        IOException cause = new IOException("Network error");
        TokenUnavailableException ex = new TokenUnavailableException(cause);

        assertEquals("Unable to obtain access token", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        TokenUnavailableException ex = new TokenUnavailableException(new IOException());
        assertTrue(ex instanceof RuntimeException);
    }
}
