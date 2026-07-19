package com.connectedneighbours.repository;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:h2:./data/admin_db;DB_CLOSE_ON_EXIT=FALSE";
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
    private static final String SQL_USERS = """
              CREATE TABLE IF NOT EXISTS users (
                    id          VARCHAR(36)  PRIMARY KEY,
                    email       VARCHAR(100) NOT NULL,
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
    private static final String SQL_DISTRICTS = """
                CREATE TABLE IF NOT EXISTS districts (
                    id   VARCHAR(36)  PRIMARY KEY,
                    name VARCHAR(200) NOT NULL
                )
            """;
    /**
     * File d'attente des écritures locales à pousser (§9.1 du design offline-sync).
     * Une ligne <em>par enregistrement sale</em> : la table est déjà l'état compacté,
     * d'où la contrainte d'unicité (entity, record_id).
     */
    private static final String SQL_PENDING_CHANGES = """
                CREATE TABLE IF NOT EXISTS pending_changes (
                    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    entity          VARCHAR(64)  NOT NULL,
                    record_id       VARCHAR(36)  NOT NULL,
                    operation       VARCHAR(8)   NOT NULL,
                    mongo_id        VARCHAR(36),
                    payload         CLOB,
                    base_updated_at VARCHAR(40),
                    occurred_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uq_pending UNIQUE (entity, record_id)
                )
            """;
    /**
     * Migrations appliquées à chaque ouverture de connexion, dans l'ordre.
     * Toutes doivent être idempotentes (IF NOT EXISTS).
     */
    private static final String[] MIGRATIONS = {
            SQL_INCIDENTS,
            SQL_USERS,
            SQL_STATISTICS,
            SQL_SYNC_LOG,
            SQL_ALERTS,
            SQL_DISTRICTS,
            SQL_PENDING_CHANGES,
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS districtId VARCHAR(36)",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS address VARCHAR(300)",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS synced BOOLEAN DEFAULT FALSE",
            // Identité serveur + jeton de concurrence optimiste (§8, §9.1).
            // mongo_id reste NULL tant que le serveur n'a pas acquitté l'INSERT.
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS mongo_id VARCHAR(36)",
            "ALTER TABLE users ADD COLUMN IF NOT EXISTS base_updated_at VARCHAR(40)",
            "ALTER TABLE incidents ADD COLUMN IF NOT EXISTS mongo_id VARCHAR(36)",
            "ALTER TABLE incidents ADD COLUMN IF NOT EXISTS base_updated_at VARCHAR(40)",
            "ALTER TABLE districts ADD COLUMN IF NOT EXISTS mongo_id VARCHAR(36)",
            // H2 considère les NULL comme distincts : plusieurs lignes locales
            // pas encore acquittées peuvent coexister sous un index unique.
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_users_mongo_id ON users(mongo_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_incidents_mongo_id ON incidents(mongo_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_districts_mongo_id ON districts(mongo_id)",
    };
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
        initSchema(connection);
    }

    /**
     * Applique le schéma et les migrations sur la connexion donnée.
     * Exposé pour les tests, qui travaillent sur une base H2 en mémoire.
     */
    public static void initSchema(Connection target) throws SQLException {
        try (Statement stmt = target.createStatement()) {
            for (String migration : MIGRATIONS) {
                stmt.executeUpdate(migration);
            }
        }
    }
}