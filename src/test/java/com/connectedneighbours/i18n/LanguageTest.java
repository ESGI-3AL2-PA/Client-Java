package com.connectedneighbours.i18n;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {

    @Test
    void frenchLanguage_hasCorrectId() {
        assertEquals("fr", Language.FRENCH.getId());
        assertEquals("Français", Language.FRENCH.getDisplayName());
        assertEquals(Locale.FRENCH, Language.FRENCH.getLocale());
    }

    @Test
    void englishLanguage_hasCorrectId() {
        assertEquals("en", Language.ENGLISH.getId());
        assertEquals("English", Language.ENGLISH.getDisplayName());
        assertEquals(Locale.ENGLISH, Language.ENGLISH.getLocale());
    }

    @Test
    void resolve_fr() {
        assertEquals(Language.FRENCH, Language.resolve("fr"));
    }

    @Test
    void resolve_en() {
        assertEquals(Language.ENGLISH, Language.resolve("en"));
    }

    @Test
    void resolve_unknown_returnsNull() {
        assertNull(Language.resolve("es"));
        assertNull(Language.resolve("unknown"));
    }

    @Test
    void equals_basedOnId() {
        assertEquals(Language.FRENCH, Language.resolve("fr"));
        assertEquals(Language.FRENCH.hashCode(), Language.resolve("fr").hashCode());
    }

    @Test
    void notEquals_differentId() {
        assertNotEquals(Language.FRENCH, Language.ENGLISH);
    }

    @Test
    void toString_returnsDisplayName() {
        assertEquals("Français", Language.FRENCH.toString());
        assertEquals("English", Language.ENGLISH.toString());
    }
}
