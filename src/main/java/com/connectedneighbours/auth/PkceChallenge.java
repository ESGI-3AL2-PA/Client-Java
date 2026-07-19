package com.connectedneighbours.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Paire verifier/challenge PKCE (RFC 7636, méthode S256).
 *
 * <p>Cette app est un client <em>public</em> : distribuée en jar, elle ne peut
 * garder aucun secret. PKCE est donc la seule preuve que l'échange du code
 * provient bien du processus qui a lancé le flow — sans lui, n'importe quel
 * programme local capable d'écouter sur le port de callback pourrait utiliser
 * un code intercepté.</p>
 */
public record PkceChallenge(String verifier, String challenge) {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 32 octets → 43 caractères base64url, le minimum imposé par la RFC. */
    private static final int VERIFIER_BYTES = 32;

    /** Génère un verifier aléatoire et son challenge S256. */
    public static PkceChallenge generate() {
        byte[] raw = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(raw);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        return new PkceChallenge(verifier, s256(verifier));
    }

    /** base64url(SHA-256(verifier)) — l'ASCII est imposé par la RFC 7636 §4.2. */
    static String s256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Valeur aléatoire opaque servant de garde CSRF sur le callback. */
    public static String randomState() {
        byte[] raw = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
