package com.connectedneighbours.i18n;

import java.util.Locale;
import java.util.Objects;

/**
 * Langue disponible dans l'application : un identifiant, un libellé natif
 * affichable et le {@link Locale} associé.
 * <p>
 * Contrairement à {@link com.connectedneighbours.theme.Theme}, il n'existe
 * pas de langue "personnalisée" chargeable dynamiquement (une traduction
 * nécessite un fichier {@code messages_xx.properties} packagé dans
 * l'application) — seules les langues built-in ci-dessous existent.
 */
public final class Language {

    public static final String ID_FR = "fr";
    public static final String ID_EN = "en";

    public static final Language FRENCH = new Language(ID_FR, "Français", Locale.FRENCH);
    public static final Language ENGLISH = new Language(ID_EN, "English", Locale.ENGLISH);

    private final String id;
    private final String displayName;
    private final Locale locale;

    private Language(String id, String displayName, Locale locale) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.locale = Objects.requireNonNull(locale, "locale");
    }

    /**
     * Résout un identifiant persisté vers une langue connue.
     *
     * @return la langue correspondante, ou {@code null} si l'id est inconnu.
     */
    public static Language resolve(String id) {
        return switch (id) {
            case ID_FR -> FRENCH;
            case ID_EN -> ENGLISH;
            default -> null;
        };
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Locale getLocale() {
        return locale;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Language other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
