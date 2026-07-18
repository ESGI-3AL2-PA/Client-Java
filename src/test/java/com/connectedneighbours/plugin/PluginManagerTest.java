package com.connectedneighbours.plugin;

import com.connectedneighbours.AppContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {

    @BeforeEach
    void setUp() {
        PluginManager.init(null);
        PluginManager.loadAll();
    }

    @AfterEach
    void tearDown() {
        PluginManager.shutdownAll();
    }

    @Test
    void loadAll_loadsBuiltinPlugins() {
        List<Plugin> plugins = PluginManager.getPlugins();
        assertNotNull(plugins);
        assertTrue(plugins.size() >= 1);
    }

    @Test
    void getPlugin_byName_helloPlugin() {
        Optional<Plugin> plugin = PluginManager.getPlugin("HelloPlugin");
        assertTrue(plugin.isPresent());
        assertEquals("HelloPlugin", plugin.get().getName());
    }

    @Test
    void getPlugin_unknown_returnsEmpty() {
        Optional<Plugin> plugin = PluginManager.getPlugin("Nonexistent");
        assertTrue(plugin.isEmpty());
    }

    @Test
    void getPlugin_null_returnsEmpty() {
        Optional<Plugin> plugin = PluginManager.getPlugin(null);
        assertTrue(plugin.isEmpty());
    }

    @Test
    void execute_unknownPlugin_doesNotThrow() {
        assertDoesNotThrow(() -> PluginManager.execute("Unknown", null));
    }

    @Test
    void execute_helloPlugin_doesNotThrow() {
        assertDoesNotThrow(() -> PluginManager.execute("HelloPlugin", null));
    }

    @Test
    void executeAll_doesNotThrow() {
        assertDoesNotThrow(() -> PluginManager.executeAll(null));
    }

    @Test
    void shutdownAll_isIdempotent() {
        PluginManager.shutdownAll();
        assertDoesNotThrow(() -> PluginManager.shutdownAll());
    }

    @Test
    void init_storesContext() {
        AppContext ctx = new AppContext();
        PluginManager.init(null);
        PluginManager.init(ctx);
        assertSame(ctx, PluginManager.getContext());
    }
}
