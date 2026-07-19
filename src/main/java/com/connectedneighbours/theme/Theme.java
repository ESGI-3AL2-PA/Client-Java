package com.connectedneighbours.theme;

import com.connectedneighbours.i18n.I18nManager;

import java.io.File;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;

/**
 * Thème JavaFX : un identifiant, un libellé affichable et
 * l'URL de la feuille de style CSS à appliquer.
 * <p>
 * Deux origines :
 * <ul>
 *   <li><b>built-in</b> — embarquée dans le classpath ({@code theme-light.css}, {@code theme-dark.css}).</li>
 *   <li><b>personnalisée</b> — un fichier {@code .css} déposé par l'utilisateur
 *       dans le dossier {@code ./themes/} (racine du CWD).</li>
 * </ul>
 */
public final class Theme {

    public static final String ID_LIGHT = "light";
    public static final String ID_DARK = "dark";

    public static final Theme LIGHT = fromClasspath(ID_LIGHT, "Clair", "/com/connectedneighbours/css/theme-light.css");
    public static final Theme DARK = fromClasspath(ID_DARK, "Sombre", "/com/connectedneighbours/css/theme-dark.css");

    private final String id;
    private final String displayName;
    private final String cssUrl;
    private final boolean builtin;

    private Theme(String id, String displayName, String cssUrl, boolean builtin) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.cssUrl = cssUrl;
        this.builtin = builtin;
    }

    /**
     * Construit un thème built-in depuis une ressource du classpath.
     *
     * @param id           identifiant
     * @param displayName  libellé affichable
     * @param resourcePath chemin classpath absolu (ex: "/com/.../theme-light.css")
     * @return le thème, ou un thème sans URL si la ressource est introuvable
     */
    public static Theme fromClasspath(String id, String displayName, String resourcePath) {
        URL url = Theme.class.getResource(resourcePath);
        String external = url != null ? url.toExternalForm() : null;
        return new Theme(id, displayName, external, true);
    }

    /**
     * Construit un thème personnalisé depuis un fichier CSS sur disque.
     * L'identifiant et le libellé affichable sont dérivés du nom du fichier
     * sans l'extension {@code .css} (ex: {@code mon-theme.css} → id "mon-theme").
     *
     * @param cssFile fichier {@code .css} (non null)
     * @return le thème personnalisé
     */
    public static Theme fromFile(File cssFile) {
        Objects.requireNonNull(cssFile, "cssFile");
        String name = cssFile.getName();
        String base = name.toLowerCase(Locale.ROOT).endsWith(".css")
                ? name.substring(0, name.length() - 4) : name;
        return new Theme(base, base, cssFile.toURI().toString(), false);
    }

    /**
     * Résout un identifiant persisté vers un thème built-in connu.
     *
     * @return le thème built-in correspondant, ou {@code null} si l'id n'est
     * pas un thème built-in (cas d'un thème personnalisé).
     */
    public static Theme resolveBuiltin(String id) {
        return switch (id) {
            case ID_LIGHT -> LIGHT;
            case ID_DARK -> DARK;
            default -> null;
        };
    }

    public String getId() {
        return id;
    }

    /**
     * Libellé affichable, traduit dynamiquement selon la langue courante
     * pour les deux thèmes built-in connus ({@link #ID_LIGHT}, {@link #ID_DARK},
     * clés {@code theme.light.name} / {@code theme.dark.name}). Tout autre
     * thème (personnalisé, ou construit via {@link #fromClasspath} avec un id
     * arbitraire) conserve le libellé fourni à la construction.
     */
    public String getDisplayName() {
        return switch (id) {
            case ID_LIGHT -> I18nManager.tr("theme.light.name");
            case ID_DARK -> I18nManager.tr("theme.dark.name");
            default -> displayName;
        };
    }

    /**
     * URL externe de la feuille CSS à ajouter à {@code Scene.getStylesheets()},
     * ou {@code null} si la ressource n'est pas disponible (thème built-in absent du classpath).
     */
    public String getCssUrl() {
        return cssUrl;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Theme other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
