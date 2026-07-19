package com.connectedneighbours.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoAuthServiceTest {

    private static final String REDIRECT = "http://127.0.0.1:51234/callback";
    private static final String STATE = "state-123";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    private String url(boolean forceReauth) {
        return new SsoAuthService().buildAuthorizeUrl(REDIRECT, STATE, CHALLENGE, forceReauth);
    }

    /**
     * Sans {@code prompt=login}, l'authorize rend un code depuis le cookie encore
     * valide du navigateur : la déconnexion reconnecte alors le même compte et
     * changer d'utilisateur devient impossible.
     */
    @Test
    void buildAuthorizeUrl_forceReauth_asksForCredentialReentry() {
        assertTrue(url(true).contains("&prompt=login"));
    }

    /** Le re-login après expiration du token doit rester silencieux. */
    @Test
    void buildAuthorizeUrl_default_keepsSilentSso() {
        assertFalse(url(false).contains("prompt"));
    }

    @Test
    void buildAuthorizeUrl_alwaysCarriesPkceS256() {
        String url = url(false);
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("code_challenge=" + CHALLENGE));
        assertTrue(url.contains("code_challenge_method=S256"));
    }
}
