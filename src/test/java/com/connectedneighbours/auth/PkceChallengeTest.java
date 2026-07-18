package com.connectedneighbours.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PkceChallengeTest {

    @Test
    void s256_matchesTheRfc7636TestVector() {
        // RFC 7636 annexe B — si ce vecteur casse, le serveur rejettera tous les échanges.
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", PkceChallenge.s256(verifier));
    }

    @Test
    void generate_producesAVerifierOfLegalLength() {
        PkceChallenge pkce = PkceChallenge.generate();
        // RFC 7636 §4.1 : 43 à 128 caractères.
        assertTrue(pkce.verifier().length() >= 43, "verifier trop court: " + pkce.verifier().length());
        assertTrue(pkce.verifier().length() <= 128, "verifier trop long: " + pkce.verifier().length());
    }

    @Test
    void generate_producesBase64UrlWithoutPadding() {
        PkceChallenge pkce = PkceChallenge.generate();
        for (String value : new String[] {pkce.verifier(), pkce.challenge()}) {
            assertFalse(value.contains("="), "padding présent: " + value);
            assertFalse(value.contains("+"), "caractère non-url: " + value);
            assertFalse(value.contains("/"), "caractère non-url: " + value);
            assertDoesNotThrow(() -> Base64.getUrlDecoder().decode(value));
        }
    }

    @Test
    void generate_challengeIsTheDigestOfItsOwnVerifier() {
        PkceChallenge pkce = PkceChallenge.generate();
        assertEquals(PkceChallenge.s256(pkce.verifier()), pkce.challenge());
    }

    @Test
    void generate_isRandomPerCall() {
        assertNotEquals(PkceChallenge.generate().verifier(), PkceChallenge.generate().verifier());
        assertNotEquals(PkceChallenge.randomState(), PkceChallenge.randomState());
    }
}
