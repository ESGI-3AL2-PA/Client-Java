package com.connectedneighbours.plugin;

import com.connectedneighbours.AppContext;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * Gère le cycle de vie des {@link Plugin}s de l'application.
 *
 * <p>Fonctionnement :
 * <ul>
 *   <li>charge les plugins built-in via {@link ServiceLoader};</li>
 *   <li>charge aussi les plugins externes {@code .jar} depuis {@code ./plugins/};</li>
 *   <li>initialise, exécute et arrête chaque plugin sans bloquer les autres en cas d'erreur;</li>
 *   <li>rend le {@link AppContext} accessible via {@link #getContext()}.</li>
 * </ul>
 *
 * <p>Les plugins sont chargés au démarrage ({@link #loadAll()}) et arrêtés à la fermeture
 * ({@link #shutdownAll()}). Les méthodes mutatrices sont {@code synchronized} et
 * {@link #shutdownAll()} est idempotente.
 */
public final class PluginManager {

    private static final Logger LOG = Logger.getLogger(PluginManager.class.getName());

    private static final String PLUGINS_DIR_NAME = "plugins";
    private static final String JAR_SUFFIX = ".jar";

    private static volatile AppContext context;

    private static List<Plugin> builtinPlugins = List.of();
    private static List<Plugin> externalPlugins = List.of();
    private static List<Plugin> plugins = List.of();

    private static URLClassLoader externalLoader;

    private PluginManager() {
    }

    public static synchronized void init(AppContext ctx) {
        context = ctx;
    }

    public static AppContext getContext() {
        return context;
    }

    public static synchronized void loadAll() {
        loadBuiltin();
        loadExternal();
        initializeAll();
        rebuildMerged();
        LOG.info("Plugins chargés : " + plugins.size()
                + " (built-in=" + builtinPlugins.size()
                + ", externes=" + externalPlugins.size() + ")");
    }

    public static List<Plugin> getPlugins() {
        return plugins;
    }

    public static Optional<Plugin> getPlugin(String name) {
        if (name == null) return Optional.empty();
        return plugins.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst();
    }

    public static void executeAll(Object input) {
        for (Plugin p : plugins) {
            safeExecute(p, input);
        }
    }

    public static void execute(String name, Object input) {
        Plugin p = getPlugin(name).orElse(null);
        if (p == null) {
            LOG.warning("Plugin introuvable, exécution ignorée : " + name);
            return;
        }
        safeExecute(p, input);
    }

    public static synchronized void shutdownAll() {
        List<Plugin> all = plugins;
        for (int i = all.size() - 1; i >= 0; i--) {
            safeShutdown(all.get(i));
        }
        builtinPlugins = List.of();
        externalPlugins = List.of();
        plugins = List.of();
        closeExternalLoader();
        context = null;
        LOG.info("Tous les plugins ont été arrêtés.");
    }

    public static File getPluginsDir() {
        File dir = new File(PLUGINS_DIR_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            LOG.warning("Impossible de créer le dossier de plugins : " + dir.getAbsolutePath());
        }
        return dir;
    }

    private static void loadBuiltin() {
        List<Plugin> list = new ArrayList<>();
        for (Plugin p : ServiceLoader.load(Plugin.class)) {
            list.add(p);
        }
        builtinPlugins = List.copyOf(list);
    }

    private static void loadExternal() {
        File dir = getPluginsDir();
        File[] jars = dir.listFiles((d, name) ->
                name != null && name.toLowerCase(Locale.ROOT).endsWith(JAR_SUFFIX));
        if (jars == null || jars.length == 0) {
            externalPlugins = List.of();
            return;
        }
        List<URL> urls = new ArrayList<>(jars.length);
        for (File j : jars) {
            if (!j.isFile()) continue;
            try {
                urls.add(j.toURI().toURL());
            } catch (Exception e) {
                LOG.warning("Jar externe ignoré (URL invalide) : " + j.getName() + " — " + e.getMessage());
            }
        }
        if (urls.isEmpty()) {
            externalPlugins = List.of();
            return;
        }
        try {
            externalLoader = new URLClassLoader(
                    urls.toArray(new URL[0]),
                    PluginManager.class.getClassLoader());
        } catch (Exception e) {
            LOG.warning("Impossible de créer le ClassLoader des plugins externes : " + e.getMessage());
            externalPlugins = List.of();
            return;
        }
        List<Plugin> list = new ArrayList<>();
        for (Plugin p : ServiceLoader.load(Plugin.class, externalLoader)) {
            list.add(p);
        }
        externalPlugins = List.copyOf(list);
    }

    private static void initializeAll() {
        for (Plugin p : builtinPlugins) {
            safeInitialize(p);
        }
        for (Plugin p : externalPlugins) {
            safeInitialize(p);
        }
    }

    private static void rebuildMerged() {
        List<Plugin> all = new ArrayList<>(builtinPlugins.size() + externalPlugins.size());
        all.addAll(builtinPlugins);
        all.addAll(externalPlugins);
        plugins = List.copyOf(all);
    }

    private static void closeExternalLoader() {
        if (externalLoader != null) {
            try {
                externalLoader.close();
            } catch (Exception e) {
                LOG.warning("Erreur lors de la fermeture du ClassLoader externe : " + e.getMessage());
            }
            externalLoader = null;
        }
    }

    private static void safeInitialize(Plugin p) {
        try {
            p.initialize();
            LOG.info("Plugin initialisé : " + p.getName() + " v" + p.getVersion());
        } catch (Throwable t) {
            LOG.warning("Échec initialize() pour le plugin " + p.getClass().getName() + " : " + t.getMessage());
        }
    }

    private static void safeExecute(Plugin p, Object input) {
        try {
            p.execute(input);
        } catch (Throwable t) {
            LOG.warning("Échec execute() pour le plugin " + p.getName() + " : " + t.getMessage());
        }
    }

    private static void safeShutdown(Plugin p) {
        try {
            p.shutdown();
            LOG.info("Plugin arrêté : " + p.getName());
        } catch (Throwable t) {
            LOG.warning("Échec shutdown() pour le plugin " + p.getName() + " : " + t.getMessage());
        }
    }
}
