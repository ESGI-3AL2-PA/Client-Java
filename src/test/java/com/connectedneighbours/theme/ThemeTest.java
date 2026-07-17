package com.connectedneighbours.theme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {

    @Test
    void lightTheme_hasCorrectId() {
        assertEquals("light", Theme.LIGHT.getId());
        assertEquals("Clair", Theme.LIGHT.getDisplayName());
        assertTrue(Theme.LIGHT.isBuiltin());
    }

    @Test
    void darkTheme_hasCorrectId() {
        assertEquals("dark", Theme.DARK.getId());
        assertEquals("Sombre", Theme.DARK.getDisplayName());
        assertTrue(Theme.DARK.isBuiltin());
    }

    @Test
    void lightTheme_hasCssUrlFromClasspath() {
        assertNotNull(Theme.LIGHT.getCssUrl());
        assertTrue(Theme.LIGHT.getCssUrl().contains("theme-light.css"));
    }

    @Test
    void darkTheme_hasCssUrlFromClasspath() {
        assertNotNull(Theme.DARK.getCssUrl());
        assertTrue(Theme.DARK.getCssUrl().contains("theme-dark.css"));
    }

    @Test
    void fromFile_createsCustomTheme(@TempDir Path tempDir) throws Exception {
        File cssFile = tempDir.resolve("custom-theme.css").toFile();
        cssFile.createNewFile();

        Theme theme = Theme.fromFile(cssFile);

        assertEquals("custom-theme", theme.getId());
        assertEquals("custom-theme", theme.getDisplayName());
        assertFalse(theme.isBuiltin());
        assertNotNull(theme.getCssUrl());
        assertTrue(theme.getCssUrl().startsWith("file:"));
    }

    @Test
    void fromFile_uppercaseExtension_handledCorrectly(@TempDir Path tempDir) throws Exception {
        File cssFile = tempDir.resolve("MyTheme.CSS").toFile();
        cssFile.createNewFile();

        Theme theme = Theme.fromFile(cssFile);
        assertEquals("MyTheme", theme.getId());
    }

    @Test
    void resolveBuiltin_light() {
        assertEquals(Theme.LIGHT, Theme.resolveBuiltin("light"));
    }

    @Test
    void resolveBuiltin_dark() {
        assertEquals(Theme.DARK, Theme.resolveBuiltin("dark"));
    }

    @Test
    void resolveBuiltin_unknown_returnsNull() {
        assertNull(Theme.resolveBuiltin("unknown"));
    }

    @Test
    void equals_basedOnId() {
        Theme a = Theme.fromFile(new File("a.css"));
        Theme b = Theme.fromFile(new File("a.css"));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEquals_differentId() {
        Theme a = Theme.fromFile(new File("a.css"));
        Theme b = Theme.fromFile(new File("b.css"));

        assertNotEquals(a, b);
    }

    @Test
    void toString_returnsDisplayName() {
        assertEquals("Clair", Theme.LIGHT.toString());
        assertEquals("Sombre", Theme.DARK.toString());
    }

    @Test
    void fromClasspath_missingResource_hasNullCssUrl() {
        Theme theme = Theme.fromClasspath("missing", "Manquant", "/nonexistent/file.css");
        assertEquals("missing", theme.getId());
        assertEquals("Manquant", theme.getDisplayName());
        assertNull(theme.getCssUrl());
    }
}
