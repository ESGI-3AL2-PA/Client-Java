package com.connectedneighbours.auth.exception;

/**
 * Levée quand le compte authentifié n'a pas un rôle administrateur.
 *
 * <p>En pratique l'auth-service refuse déjà d'émettre un code d'autorisation
 * pour ces comptes, donc ce cas ne se produit que si la vérification serveur
 * a été contournée. On échoue explicitement plutôt que d'ouvrir un tableau de
 * bord d'administration à un compte qui n'y a pas droit.</p>
 */
public class NotAdminException extends RuntimeException {

    public NotAdminException(String role) {
        super("Ce compte n'est pas administrateur (rôle : " + (role == null ? "inconnu" : role) + ")");
    }
}
