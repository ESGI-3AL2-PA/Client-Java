package com.connectedneighbours.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Gestion centralisée de la langue courante de l'application.
 * <p>
 * Responsabilités :
 * <ul>
 *   <li>persister la langue courante dans les {@link Preferences} utilisateur
 *       (clé {@code language.id}) ;</li>
 *   <li>exposer le {@link ResourceBundle} correspondant ({@code i18n.messages_xx.properties}) ;</li>
 *   <li>fournir un helper de traduction ({@link #tr(String)}) tolérant aux
 *       clés manquantes (ne lève jamais, retourne la clé brute en secours).</li>
 * </ul>
 * <p>
 * À la différence de {@link com.connectedneighbours.theme.ThemeManager}, il
 * n'y a pas de méthode {@code applyLanguage(Scene)} : un changement de langue
 * ne peut être appliqué qu'au chargement d'un FXML (via
 * {@code FXMLLoader.setResources(getBundle())}) ou par appel direct de
 * {@link #tr(String)} dans le code Java — il n'existe pas de mécanisme de
 * re-binding dynamique du texte déjà affiché sans reconstruire l'écran.
 */
public final class I18nManager {

    private static final Logger LOG = Logger.getLogger(I18nManager.class.getName());

    private static final Preferences PREFS = Preferences.userNodeForPackage(I18nManager.class);
    private static final String KEY_LANGUAGE_ID = "language.id";
    private static final String BASE_BUNDLE_NAME = "i18n.messages";

    private I18nManager() {
    }

    //  Persistance

    /**
     * @return la langue courante persistée, ou {@link Language#FRENCH} par défaut
     *         (et en fallback si l'id persisté ne correspond plus à rien).
     */
    public static Language getCurrent() {
        String id = PREFS.get(KEY_LANGUAGE_ID, Language.ID_FR);
        Language lang = Language.resolve(id);
        return lang != null ? lang : Language.FRENCH;
    }

    /**
     * Persiste la langue courante. Ne recharge rien : le changement sera
     * visible à la prochaine reconstruction d'écran (ou après un appel
     * explicite de reload).
     */
    public static void setCurrent(Language language) {
        Objects.requireNonNull(language, "language");
        PREFS.put(KEY_LANGUAGE_ID, language.getId());
    }

    /**
     * @return liste non modifiable des langues disponibles.
     */
    public static List<Language> getAvailableLanguages() {
        return List.of(Language.FRENCH, Language.ENGLISH);
    }

    //  Bundle / traduction

    /**
     * @return le {@link ResourceBundle} correspondant à la langue courante.
     * S'appuie sur le cache natif de {@link ResourceBundle#getBundle} —
     * pas de cache custom à invalider.
     */
    public static ResourceBundle getBundle() {
        return ResourceBundle.getBundle(BASE_BUNDLE_NAME, getCurrent().getLocale());
    }

    /**
     * Traduit une clé dans la langue courante. Ne lève jamais : si la clé est
     * absente du bundle, elle est retournée telle quelle (et un avertissement
     * est loggé) plutôt que de faire planter l'UI.
     */
    public static String tr(String key) {
        try {
            return getBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.warning("Clé i18n manquante : " + key);
            return key;
        }
    }

    /**
     * Traduit une clé paramétrée via {@link MessageFormat} (arguments
     * substitués aux {@code {0}}, {@code {1}}...). N'utiliser que sur des
     * clés dont la valeur échappe correctement les apostrophes littérales
     * (doublées en {@code ''}) — {@link #tr(String)} seul n'a pas ce risque.
     */
    public static String tr(String key, Object... args) {
        String pattern = tr(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            LOG.warning("Pattern MessageFormat invalide pour la clé " + key + " : " + e.getMessage());
            return pattern;
        }
    }
}
