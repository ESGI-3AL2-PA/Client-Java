package com.connectedneighbours.controller;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/**
 * Boutons de dialogue standards, libellés en français.
 *
 * <p>Les constantes de {@link ButtonType} ({@code CANCEL}, {@code YES},
 * {@code NO}…) tirent leur libellé du bundle de JavaFX, qui n'est pas traduit
 * pour la locale de cette JVM : l'interface est entièrement en français mais
 * les dialogues affichaient « Cancel », « Yes » et « No ». On fixe le texte
 * ici au lieu de dépendre de la locale de la machine.</p>
 *
 * <p>{@code OK} n'est pas repris : le libellé est identique dans les deux
 * langues.</p>
 */
final class Buttons {

    static final ButtonType CANCEL = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
    static final ButtonType YES = new ButtonType("Oui", ButtonBar.ButtonData.YES);
    static final ButtonType NO = new ButtonType("Non", ButtonBar.ButtonData.NO);

    private Buttons() {
    }
}
