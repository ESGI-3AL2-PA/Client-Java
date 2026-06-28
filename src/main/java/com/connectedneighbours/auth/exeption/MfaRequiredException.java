package com.connectedneighbours.auth.exeption;

/**
 * Levée par /auth/login lorsqu'il renvoie 202 (MFA requis).
 * Porte le mfa_token à renvoyer à /auth/login/mfa accompagné du code TOTP.
 */
public class MfaRequiredException extends RuntimeException {

    private final String mfaToken;

    public MfaRequiredException(String mfaToken) {
        super("MFA required");
        this.mfaToken = mfaToken;
    }

    public String getMfaToken() {
        return mfaToken;
    }
}
