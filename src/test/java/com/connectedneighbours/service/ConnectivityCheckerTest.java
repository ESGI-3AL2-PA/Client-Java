package com.connectedneighbours.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectivityCheckerTest {

    @Test
    void isOnline_returnsTrue_whenLambdaReturnsTrue() {
        ConnectivityChecker checker = () -> true;
        assertTrue(checker.isOnline());
    }

    @Test
    void isOnline_returnsFalse_whenLambdaReturnsFalse() {
        ConnectivityChecker checker = () -> false;
        assertFalse(checker.isOnline());
    }
}
