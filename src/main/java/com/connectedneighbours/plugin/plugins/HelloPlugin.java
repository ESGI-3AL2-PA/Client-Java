package com.connectedneighbours.plugin.plugins;

import com.connectedneighbours.plugin.Plugin;
import com.sun.webkit.plugin.PluginManager;

import java.util.logging.Logger;

/**
 * Plugin de validation : prouve que {@link java.util.ServiceLoader} câble bien
 * le cycle de vie {@link PluginManager}. Ne fait rien d'utile — à retirer une
 * fois les vrais plugins (ExportStats, SocialAnalysis, LocalCalendar) inscrits.
 */
public class HelloPlugin implements Plugin {

    private static final Logger LOG = Logger.getLogger(HelloPlugin.class.getName());

    @Override
    public String getName() {
        return "HelloPlugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void initialize() {
        LOG.info("HelloPlugin initialized");
    }

    @Override
    public void execute(Object context) {
        LOG.info("HelloPlugin executed (context=" + (context == null ? "null" : context.getClass().getSimpleName()) + ")");
    }

    @Override
    public void shutdown() {
        LOG.info("HelloPlugin shutdown");
    }
}
