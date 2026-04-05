package com.connectedneighbours.repository;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:h2:./data/admin_db;AUTO_SERVER=TRUE";
    private static final String DB_USER = "admin";
    private static final String DB_PASS = "";
    private static final String SQL_INCIDENTS = """
                CREATE TABLE IF NOT EXISTS incidents (
                    id          VARCHAR(36)  PRIMARY KEY,
                    title       VARCHAR(255) NOT NULL,
                    description TEXT,
                    status      VARCHAR(50)  DEFAULT 'OPEN',
                    priority    VARCHAR(20)  DEFAULT 'NORMAL',
                    author_id   VARCHAR(36),
                    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    synced      BOOLEAN      DEFAULT FALSE
                )
            """;
    private static final String SQL_ALERTS = """
                CREATE TABLE IF NOT EXISTS alerts (
                    id          VARCHAR(36)  PRIMARY KEY,
                    message     TEXT         NOT NULL,
                    type        VARCHAR(50),
                    read        BOOLEAN      DEFAULT FALSE,
                    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    synced      BOOLEAN      DEFAULT FALSE
                )
            """;
    private static final String SQL_STATISTICS = """
                CREATE TABLE IF NOT EXISTS statistics (
                    id          INTEGER      PRIMARY KEY AUTO_INCREMENT,
                    metric_key  VARCHAR(100) NOT NULL,
                    metric_value DOUBLE      NOT NULL,
                    period      VARCHAR(20),
                    recorded_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """;
    private static final String SQL_SYNC_LOG = """
                CREATE TABLE IF NOT EXISTS sync_log (
                    id          INTEGER      PRIMARY KEY AUTO_INCREMENT,
                    table_name  VARCHAR(50),
                    record_id   VARCHAR(36),
                    action      VARCHAR(20),
                    conflict    BOOLEAN      DEFAULT FALSE,
                    synced_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
                )
            """;
    private static Connection connection;

    public DatabaseManager() {
    }

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            System.out.println(("Ouverture de la connexion H2..."));
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            initSchema();
            System.out.println("BDD H2 prête.");
        }
        return connection;
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Connexion H2 fermée.");
            }
        } catch (SQLException e) {
            System.out.println("Erreur fermeture H2");
        }
    }

    private static void initSchema() throws SQLException {
        System.out.println("Initialisation du schéma...");
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(SQL_INCIDENTS);
            stmt.executeUpdate(SQL_ALERTS);
            stmt.executeUpdate(SQL_STATISTICS);
            stmt.executeUpdate(SQL_SYNC_LOG);
        }
    }
}