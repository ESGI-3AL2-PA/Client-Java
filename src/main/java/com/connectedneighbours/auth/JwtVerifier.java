package com.connectedneighbours.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.connectedneighbours.config.AuthConfig;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;

/**
 * Validation locale des access tokens JWT (RS256) via le endpoint JWKS
 * du auth-service.
 *
 * <p>Le cache des clés a une durée de vie bornée. Un cache par {@code kid} sans
 * expiration — ce qu'utilisait la version précédente — ne repère jamais une
 * rotation de clé : le processus reste sur l'ancienne clé publique jusqu'à son
 * redémarrage, soit, pour une application de bureau, jusqu'à ce que
 * l'utilisateur relance le logiciel.</p>
 */
public class JwtVerifier {

    /** Marge d'horloge acceptée sur exp/iat, alignée sur celle de {@code isExpired}. */
    private static final long LEEWAY_SECONDS = 30;

    private final JwkProvider jwkProvider;

    public JwtVerifier() {
        try {
            this.jwkProvider = new JwkProviderBuilder(new URL(AuthConfig.getJwksUrl()))
                    .cached(5, 10, TimeUnit.MINUTES)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("JWKS URL invalide: " + AuthConfig.getJwksUrl(), e);
        }
    }

    /**
     * Vérifie la signature, l'issuer et l'audience du token.
     * Lève une exception si invalide. Sinon renvoie le JWT décodé.
     */
    public DecodedJWT verify(String token) {
        try {
            DecodedJWT unverified = JWT.decode(token);
            String kid = unverified.getKeyId();
            if (kid == null) {
                throw new IllegalStateException("JWT sans kid");
            }
            RSAPublicKey publicKey = loadKey(kid);
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            return JWT.require(algorithm)
                    .withIssuer(AuthConfig.JWT_ISSUER)
                    .withAudience(AuthConfig.JWT_AUDIENCE)
                    .acceptLeeway(LEEWAY_SECONDS)
                    .build()
                    .verify(token);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("JWT invalide: " + e.getMessage(), e);
        }
    }

    /**
     * Indique si le token est encore valide (signature + expiration).
     * Aucune exception levée — retourne false en cas de problème.
     */
    public boolean isValid(String token) {
        try {
            verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private RSAPublicKey loadKey(String kid) {
        try {
            Jwk jwk = jwkProvider.get(kid);
            return (RSAPublicKey) jwk.getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("Clé JWKS introuvable pour kid=" + kid, e);
        }
    }
}
