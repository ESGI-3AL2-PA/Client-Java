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
                    reporterId  VARCHAR(36)  NOT NULL,
                    districtId  VARCHAR(36)  NOT NULL,
                    category    VARCHAR(50)  NOT NULL,
                    description TEXT,
                    photoUrl    VARCHAR(300),
                    status      VARCHAR(50)  DEFAULT 'OPEN',
                    assignedTo  VARCHAR(36),
                    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    synced      BOOLEAN      DEFAULT FALSE
                )
            """;
    private static final String SQL_USERS = """
              CREATE TABLE IF NOT EXISTS users (
                    id          VARCHAR(36)  PRIMARY KEY,
                    email       VARCHAR(100) NOT NULL UNIQUE,
                    firstName   VARCHAR(100),
                    lastName    VARCHAR(100),
                    phone       VARCHAR(100),
                    role        VARCHAR(20)  NOT NULL,
                    status      VARCHAR(20),
                    balance     DOUBLE,
                    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
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
            connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            initSchema();
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
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(SQL_INCIDENTS);
            stmt.executeUpdate(SQL_USERS);
            stmt.executeUpdate(SQL_STATISTICS);
            stmt.executeUpdate(SQL_SYNC_LOG);
        }
    }
}