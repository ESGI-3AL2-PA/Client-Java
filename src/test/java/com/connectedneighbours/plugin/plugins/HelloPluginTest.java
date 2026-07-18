package com.connectedneighbours.plugin.plugins;

import com.connectedneighbours.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HelloPluginTest {

    private HelloPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new HelloPlugin();
    }

    @AfterEach
    void tearDown() {
        plugin.shutdown();
    }

    @Test
    void implementsPlugin() {
        assertTrue(plugin instanceof Plugin);
    }

    @Test
    void getName_returnsHelloPlugin() {
        assertEquals("HelloPlugin", plugin.getName());
    }

    @Test
    void getVersion_returnsOneDotZero() {
        assertEquals("1.0.0", plugin.getVersion());
    }

    @Test
    void initialize_doesNotThrow() {
        assertDoesNotThrow(() -> plugin.initialize());
    }

    @Test
    void execute_doesNotThrow() {
        plugin.initialize();
        assertDoesNotThrow(() -> plugin.execute("test context"));
    }

    @Test
    void shutdown_doesNotThrow() {
        plugin.initialize();
        assertDoesNotThrow(() -> plugin.shutdown());
    }
}
