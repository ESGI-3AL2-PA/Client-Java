package com.connectedneighbours.theme;

import javafx.scene.Scene;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.logging.Logger;

/**
 * Gestion centralisée des thèmes JavaFX.
 * <p>
 * Responsabilités :
 * <ul>
 *   <li>persister le thème courant dans les {@link Preferences} utilisateur
 *       (clé {@code theme.id}) ;</li>
 *   <li>exposer la liste des thèmes disponibles = 2 thèmes built-in
 *       ({@link Theme#LIGHT}, {@link Theme#DARK})
 *       + les thèmes personnalisés scannés dans {@code ./themes/*.css} ;</li>
 *   <li>appliquer le thème courant sur une {@link Scene} en remplaçant
 *       dynamiquement la feuille de style.</li>
 * </ul>
 *
 * <h2>Thèmes personnalisés</h2>
 * L'utilisateur dépose ses fichiers {@code .css} dans le dossier
 * {@code ./themes/} (relatif au répertoire de travail, comme {@code ./data/}
 * pour la BDD H2). Le dossier est créé automatiquement au premier appel de
 * {@link #getThemesDir()}. Le nom affiché est dérivé du nom du fichier sans
 * extension. Un fichier dont le nom (sans extension) collide avec un id
 * built-in ({@code light}, {@code dark}) est ignoré pour éviter toute
 * ambiguïté.
 *
 * <h2>Application à chaud</h2>
 * {@link #applyTheme(Scene)} retire les feuilles de thème précédemment
 * ajoutées par ce gestionnaire puis ajoute celle du thème courant. Toute
 * autre feuille (ex: ajoutée manuellement) est conservée. Le suivi se fait
 * via un préfixe d'URL enregistré dans les propriétés utilisateur de la
 * scène ({@link #SCENE_KEY}).
 */
public final class ThemeManager {

    private static final Logger LOG = Logger.getLogger(ThemeManager.class.getName());

    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
    private static final String KEY_THEME_ID = "theme.id";

    private static final String THEMES_DIR_NAME = "themes";

    /** Clé de propriété de scène pour tracker l'URL du thème courant. */
    private static final String SCENE_KEY = "themeManager.currentUrl";

    private static List<Theme> customThemes = List.of();

    private ThemeManager() {
    }

    //  Persistance

    /**
     * @return le thème courant persisté, ou {@link Theme#LIGHT} par défaut
     *         (et en fallback si l'id persisté ne correspond plus à rien).
     */
    public static Theme getCurrent() {
        String id = PREFS.get(KEY_THEME_ID, Theme.ID_LIGHT);
        Theme builtin = Theme.resolveBuiltin(id);
        if (builtin != null) {
            return builtin;
        }
        // id perso : on cherche parmi les thèmes personnalisés déjà scannés.
        return customThemes.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(Theme.LIGHT);
    }

    /**
     * Persiste le thème courant. Ne recharge pas les scènes : le changement
     * sera visible à la prochaine navigation (ou après un appel explicite à
     * {@link #applyTheme(Scene)}).
     */
    public static void setCurrent(Theme theme) {
        Objects.requireNonNull(theme, "theme");
        PREFS.put(KEY_THEME_ID, theme.getId());
    }

    //  Liste des thèmes

    /**
     * @return liste non modifiable des thèmes disponibles : les 2 built-in
     *         puis les thèmes personnalisés.
     */
    public static List<Theme> getAvailableThemes() {
        List<Theme> all = new ArrayList<>(2 + customThemes.size());
        all.add(Theme.LIGHT);
        all.add(Theme.DARK);
        all.addAll(customThemes);
        return List.copyOf(all);
    }

    /**
     * Re-scanne le dossier {@code ./themes/} et rafraîchit la liste interne
     * des thèmes personnalisés. À appeler au démarrage de l'app et quand
     * l'utilisateur clique sur « Recharger » dans les Paramètres.
     */
    public static synchronized void reloadCustomThemes() {
        File dir = getThemesDir();
        File[] files = dir.listFiles((d, name) ->
                name != null && name.toLowerCase(Locale.ROOT).endsWith(".css"));
        if (files == null || files.length == 0) {
            customThemes = List.of();
            return;
        }
        List<Theme> list = new ArrayList<>(files.length);
        for (File f : files) {
            if (!f.isFile()) continue;
            String base = stripCssExtension(f.getName()).toLowerCase(Locale.ROOT);
            // On ignore les fichiers dont le nom collide avec un built-in.
            if (isReservedId(base)) {
                LOG.warning("Thème personnalisé ignoré (nom réservé) : " + f.getName());
                continue;
            }
            list.add(Theme.fromFile(f));
        }
        list.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));
        customThemes = List.copyOf(list);
    }

    /**
     * @return le dossier {@code ./themes/}, créé s'il n'existe pas encore.
     *         Jamais null (mais peut être un chemin non inscriptible).
     */
    public static File getThemesDir() {
        File dir = new File(THEMES_DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            LOG.warning("Impossible de créer le dossier de thèmes : " + dir.getAbsolutePath());
        }
        return dir;
    }

    //  Application aux scènes

    /**
     * Applique le thème courant : retire la feuille de thème
     * précédente puis ajoute celle
     * du thème courant.
     */
    public static void applyTheme(Scene scene) {
        if (scene == null) return;
        Theme theme = getCurrent();
        String newUrl = theme.getCssUrl();

        // Retire l'ancienne feuille de thème (si présente).
        Object previous = scene.getProperties().get(SCENE_KEY);
        if (previous instanceof String oldUrl && !oldUrl.isEmpty()) {
            scene.getStylesheets().remove(oldUrl);
        }

        if (newUrl == null || newUrl.isEmpty()) {
            scene.getProperties().remove(SCENE_KEY);
            return;
        }

        if (!scene.getStylesheets().contains(newUrl)) {
            scene.getStylesheets().add(newUrl);
        }
        scene.getProperties().put(SCENE_KEY, newUrl);
    }

    /**
     * Applique un thème explicite à la scène (sans changer le thème persisté).
     * Utile pour un aperçu live dans une fenêtre modale.
     */
    public static void applyTheme(Scene scene, Theme theme) {
        if (scene == null || theme == null) return;
        String newUrl = theme.getCssUrl();

        Object previous = scene.getProperties().get(SCENE_KEY);
        if (previous instanceof String oldUrl && !oldUrl.isBlank()) {
            scene.getStylesheets().remove(oldUrl);
        }

        if (newUrl == null || newUrl.isBlank()) {
            scene.getProperties().remove(SCENE_KEY);
            return;
        }

        if (!scene.getStylesheets().contains(newUrl)) {
            scene.getStylesheets().add(newUrl);
        }
        scene.getProperties().put(SCENE_KEY, newUrl);
    }

    //  Helpers

    private static String stripCssExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".css")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static boolean isReservedId(String id) {
        return Theme.ID_LIGHT.equals(id)
                || Theme.ID_DARK.equals(id);
    }

    /**
     * Recherche un thème par id parmi les thèmes actuellement disponibles.
     * @return le thème correspondant, ou empty si introuvable.
     */
    public static Optional<Theme> findById(String id) {
        if (id == null) return Optional.empty();
        return getAvailableThemes().stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst();
    }
}
