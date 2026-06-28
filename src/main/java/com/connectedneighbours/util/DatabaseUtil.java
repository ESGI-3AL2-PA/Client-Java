package com.connectedneighbours.util;

import com.connectedneighbours.repository.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseUtil {

    /**
     * Exécute une requête de mise à jour (INSERT, UPDATE, DELETE).
     *
     * @param sql    La requête SQL paramétrée (avec des ?)
     * @param params Les paramètres à insérer dans la requête
     * @throws SQLException Si une erreur survient
     */
    public static int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParameters(stmt, params);
            return stmt.executeUpdate();
        }
    }

    /**
     * Exécute une requête de sélection (SELECT) et mappe le résultat vers une liste d'objets.
     *
     * @param sql       La requête SQL paramétrée (avec des ?)
     * @param rowMapper Fonction pour mapper chaque ligne du ResultSet vers un objet de type T
     * @param params    Les paramètres à insérer dans la requête
     * @return Une liste contenant les résultats
     * @throws SQLException Si une erreur survient
     */
    public static <T> List<T> executeQuery(String sql, RowMapper<T> rowMapper, Object... params) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(rowMapper.mapRow(rs));
                }
                return results;
            }
        }
    }

    private static void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
        }
    }

    public interface RowMapper<T> {
        T mapRow(ResultSet rs) throws SQLException;
    }
}

