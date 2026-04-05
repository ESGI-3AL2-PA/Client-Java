package com.connectedneighbours.plugin;

public interface Plugin {
    String getName();

    String getVersion();

    void initialize();

    void execute(Object context);

    void shutdown();
}