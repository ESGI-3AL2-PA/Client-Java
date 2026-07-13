package com.connectedneighbours.repository;

import java.io.IOException;

/**
 * Quand le serveur répond avec un code d'erreur HTTP.
 * Permet aux appelants de distinguer un 404 (ressource inexistante) d'une
 * véritable erreur réseau.
 */
public class ApiException extends IOException {

    private final int statusCode;

    public ApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Vrai si le code HTTP est 404 Not Found.
     */
    public boolean isNotFound() {
        return statusCode == 404;
    }
}

