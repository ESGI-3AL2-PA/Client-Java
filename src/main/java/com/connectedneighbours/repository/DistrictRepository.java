package com.connectedneighbours.repository;

import com.connectedneighbours.model.District;
import com.connectedneighbours.util.DatabaseUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DistrictRepository {

    private static final Logger LOG = Logger.getLogger(DistrictRepository.class.getName());

    public List<District> findAll() {
        String sql = "SELECT * FROM districts";
        try {
            return DatabaseUtil.executeQuery(sql, this::extractDistrict);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return List.of();
        }
    }

    public Optional<District> findById(String id) {
        String sql = "SELECT * FROM districts WHERE id = ?";
        try {
            List<District> districts = DatabaseUtil.executeQuery(sql, this::extractDistrict, id);
            return districts.isEmpty() ? Optional.empty() : Optional.of(districts.get(0));
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
            return Optional.empty();
        }
    }

    public void save(District district) {
        String sql = "INSERT INTO districts (id, name) VALUES (?, ?)";
        try {
            DatabaseUtil.executeUpdate(sql,
                    district.getId(),
                    district.getName()
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void update(District district) {
        String sql = "UPDATE districts SET name = ? WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    district.getName(),
                    district.getId()
            );
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public void delete(String id) {
        String sql = "DELETE FROM districts WHERE id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, id);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Erreur SQL: " + sql, e);
        }
    }

    public Optional<District> findByMongoId(String mongoId) {
        String sql = "SELECT * FROM districts WHERE mongo_id = ?";
        try {
            List<District> districts = DatabaseUtil.executeQuery(sql, this::extractDistrict, mongoId);
            return districts.isEmpty() ? Optional.empty() : Optional.of(districts.get(0));
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * Les quartiers sont une entité <em>à sens unique</em> (§5.3) : gérés sur le
     * web, ils ne font que descendre. Ils n'ont pas d'{@code updatedAt}, donc
     * pas de jeton de concurrence optimiste — il n'y a rien à confronter
     * puisque le client ne les modifie jamais.
     *
     * <p>La liste locale sert le menu déroulant du formulaire d'incident et la
     * résolution id → nom, qui fonctionnent donc hors-ligne.</p>
     */
    public void saveFromSync(District district, String mongoId) {
        // MERGE et non INSERT : le pull rejoue des quartiers déjà connus et
        // l'INSERT y violait la clé primaire (cf. UserRepository#saveFromSync).
        String sql = "MERGE INTO districts (id, name, mongo_id) KEY (id) VALUES (?, ?, ?)";
        try {
            DatabaseUtil.executeUpdate(sql,
                    district.getId(),
                    district.getName(),
                    mongoId
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateFromSync(District district, String mongoId) {
        String sql = "UPDATE districts SET name = ? WHERE mongo_id = ?";
        try {
            DatabaseUtil.executeUpdate(sql,
                    district.getName(),
                    mongoId
            );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteFromSync(String mongoId) {
        String sql = "DELETE FROM districts WHERE mongo_id = ?";
        try {
            DatabaseUtil.executeUpdate(sql, mongoId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private District extractDistrict(ResultSet rs) throws SQLException {
        District district = new District();
        district.setId(rs.getString("id"));
        district.setName(rs.getString("name"));
        return district;
    }
}
