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

    private District extractDistrict(ResultSet rs) throws SQLException {
        District district = new District();
        district.setId(rs.getString("id"));
        district.setName(rs.getString("name"));
        return district;
    }
}
