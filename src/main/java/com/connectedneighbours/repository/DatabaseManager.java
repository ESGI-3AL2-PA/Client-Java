package com.connectedneighbours.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:h2:./data/admin_db;AUTO_SERVER=TRUE";
    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(DB_URL, "admin", "");
            initSchema();
        }
        return connection;
    }

    private static void initSchema() throws SQLException {
        String createIncidents = """
                    CREATE TABLE IF NOT EXISTS incidents (
                        id          VARCHAR(36) PRIMARY KEY,
                        title       VARCHAR(255) NOT NULL,
                        description TEXT,
                        status      VARCHAR(50),
                        created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        synced      BOOLEAN DEFAULT FALSE,
                        updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createIncidents);
        }
    }
}