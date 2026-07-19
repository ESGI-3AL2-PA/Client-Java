package com.connectedneighbours.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class I18nManagerTest {

    @Test
    void getAvailableLanguages_containsFrenchAndEnglish() {
        assertTrue(I18nManager.getAvailableLanguages().contains(Language.FRENCH));
        assertTrue(I18nManager.getAvailableLanguages().contains(Language.ENGLISH));
        assertEquals(2, I18nManager.getAvailableLanguages().size());
    }

    @Test
    void bundle_loadsWithoutException_forFrench() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Language.FRENCH.getLocale());
        assertNotNull(bundle);
    }

    @Test
    void bundle_loadsWithoutException_forEnglish() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Language.ENGLISH.getLocale());
        assertNotNull(bundle);
    }

    @Test
    void tr_missingKey_returnsKeyItself_doesNotThrow() {
        assertEquals("this.key.does.not.exist", I18nManager.tr("this.key.does.not.exist"));
    }

    @Test
    void tr_withArgs_missingKey_returnsKeyItself_doesNotThrow() {
        assertEquals("this.key.does.not.exist", I18nManager.tr("this.key.does.not.exist", "arg"));
    }

    @Test
    void tr_withArgs_realParameterizedKey_substitutesArgument() {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", Language.ENGLISH.getLocale());
        String pattern = bundle.getString("incidents.count.singular");
        assertEquals("1 incident", java.text.MessageFormat.format(pattern, 1));
    }

    @Test
    void keySets_match_betweenFrenchAndEnglishBundles() throws IOException {
        Set<String> frKeys = loadRawKeys("/i18n/messages_fr.properties");
        Set<String> enKeys = loadRawKeys("/i18n/messages_en.properties");
        assertEquals(frKeys, enKeys, "Le jeu de clés doit être identique entre messages_fr.properties et messages_en.properties");
    }

    private static Set<String> loadRawKeys(String resourcePath) throws IOException {
        Properties props = new Properties();
        try (InputStream is = I18nManagerTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Ressource introuvable : " + resourcePath);
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }
        return props.stringPropertyNames();
    }
}
