package com.connectedneighbours.controller;

import com.connectedneighbours.model.Incident;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compteurs des cartes du tableau de bord. Extraits en statiques précisément
 * pour être vérifiables sans toolkit JavaFX — il n'existe pas de test de
 * contrôleur dans ce projet.
 */
class DashboardControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 19, 12, 0);
    private static final LocalDateTime SINCE = NOW.minusDays(30);

    private static Incident incident(String status, LocalDateTime updatedAt) {
        Incident i = new Incident();
        i.setStatus(status);
        i.setUpdatedAt(updatedAt);
        return i;
    }

    private static Incident resolved(LocalDateTime updatedAt) {
        return incident(Incident.Status.RESOLVED.getValue(), updatedAt);
    }

    @Test
    void countByStatus_countsOnlyTheRequestedStatus() {
        List<Incident> incidents = List.of(
                incident(Incident.Status.OPEN.getValue(), NOW),
                incident(Incident.Status.OPEN.getValue(), NOW),
                incident(Incident.Status.IN_PROGRESS.getValue(), NOW),
                resolved(NOW)
        );

        assertEquals(2, DashboardController.countByStatus(incidents, Incident.Status.OPEN));
        assertEquals(1, DashboardController.countByStatus(incidents, Incident.Status.IN_PROGRESS));
        assertEquals(1, DashboardController.countByStatus(incidents, Incident.Status.RESOLVED));
    }

    @Test
    void countByStatus_emptyList() {
        assertEquals(0, DashboardController.countByStatus(List.of(), Incident.Status.OPEN));
    }

    /**
     * La carte est libellée « Résolus (30j) » : elle comptait jusqu'ici tous les
     * résolus, sans fenêtre.
     */
    @Test
    void countResolvedSince_excludesOlderThanTheWindow() {
        List<Incident> incidents = List.of(
                resolved(NOW.minusDays(1)),
                resolved(NOW.minusDays(29)),
                resolved(NOW.minusDays(31)),
                resolved(NOW.minusYears(1))
        );

        assertEquals(2, DashboardController.countResolvedSince(incidents, SINCE));
    }

    @Test
    void countResolvedSince_ignoresIncidentsThatAreNotResolved() {
        List<Incident> incidents = List.of(
                incident(Incident.Status.OPEN.getValue(), NOW),
                incident(Incident.Status.IN_PROGRESS.getValue(), NOW),
                resolved(NOW)
        );

        assertEquals(1, DashboardController.countResolvedSince(incidents, SINCE));
    }

    /** Un incident jamais réécrit n'a pas de date de résolution exploitable. */
    @Test
    void countResolvedSince_ignoresNullUpdatedAt() {
        assertEquals(0, DashboardController.countResolvedSince(List.of(resolved(null)), SINCE));
    }

    @Test
    void countResolvedSince_boundaryIsExclusive() {
        assertEquals(0, DashboardController.countResolvedSince(List.of(resolved(SINCE)), SINCE));
        assertEquals(1, DashboardController.countResolvedSince(
                List.of(resolved(SINCE.plusSeconds(1))), SINCE));
    }
}
