package com.connectedneighbours.auth.exeption;

/**
 * Levée quand l'access token ne peut être obtenu (refresh échoué, réseau, etc.).
 * Wrappe l'IOException originel pour passer dans un Supplier<String>.
 */
public class TokenUnavailableException extends RuntimeException {

    public TokenUnavailableException(Throwable cause) {
        super("Unable to obtain access token", cause);
    }
}
