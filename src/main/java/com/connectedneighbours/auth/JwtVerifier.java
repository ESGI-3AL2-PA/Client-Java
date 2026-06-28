package com.connectedneighbours.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.connectedneighbours.config.AuthConfig;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validation locale des access tokens JWT (RS256) via le endpoint JWKS
 * du auth-service. Aucun appel réseau n'est fait par requête API : la
 * clé publique correspondant au kid est récupérée une fois puis cachée.
 */
public class JwtVerifier {

    private final JwkProvider jwkProvider;
    private final ConcurrentHashMap<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();

    public JwtVerifier() {
        try {
            this.jwkProvider = new UrlJwkProvider(new URL(AuthConfig.getJwksUrl()));
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
            RSAPublicKey publicKey = keyCache.computeIfAbsent(kid, this::loadKey);
            Algorithm algorithm = Algorithm.RSA256(publicKey, null);
            return JWT.require(algorithm)
                    .withIssuer(AuthConfig.JWT_ISSUER)
                    .withAudience(AuthConfig.JWT_AUDIENCE)
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
