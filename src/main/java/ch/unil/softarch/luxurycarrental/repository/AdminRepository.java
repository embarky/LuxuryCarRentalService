package ch.unil.softarch.luxurycarrental.repository;

import ch.unil.softarch.luxurycarrental.domain.entities.Admin;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.*;
import java.util.*;

/**
 * Repository for Admin entity using JDBC to persist in the database.
 */
@ApplicationScoped
public class AdminRepository implements CrudRepository<Admin> {

    /**
     * Save or update an admin in the database.
     * @param admin Admin object to save or update
     * @return saved Admin object
     */
    @Override
    public Admin save(Admin admin) {
        String sql = """
            INSERT INTO admin (id, name, username, email, password, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                username = VALUES(username),
                email = VALUES(email),
                password = VALUES(password),
                updated_at = VALUES(updated_at)
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, admin.getId().toString());
            stmt.setString(2, admin.getName());
            stmt.setString(3, admin.getUsername());
            stmt.setString(4, admin.getEmail());
            stmt.setString(5, admin.getPassword());
            stmt.setTimestamp(6, Timestamp.valueOf(admin.getCreatedAt()));
            stmt.setTimestamp(7, Timestamp.valueOf(admin.getUpdatedAt()));

            stmt.executeUpdate();
            return admin;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save admin", e);
        }
    }

    /**
     * Find an admin by ID.
     * @param id Admin UUID
     * @return Optional containing Admin if found
     */
    @Override
    public Optional<Admin> findById(UUID id) {
        String sql = "SELECT * FROM admin WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Admin admin = mapResultSetToAdmin(rs);
                return Optional.of(admin);
            }

            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find admin by ID", e);
        }
    }

    /**
     * Return all admins from the database.
     * @return list of Admin objects
     */
    @Override
    public List<Admin> findAll() {
        String sql = "SELECT * FROM admin";
        List<Admin> admins = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                admins.add(mapResultSetToAdmin(rs));
            }

            return admins;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve admins", e);
        }
    }

    /**
     * Delete an admin by ID.
     * @param id Admin UUID
     * @return true if deleted successfully
     */
    @Override
    public boolean delete(UUID id) {
        String sql = "DELETE FROM admin WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete admin", e);
        }
    }

    // ---------- Custom queries ----------

    /**
     * Find admin by email.
     * @param email admin email
     * @return Optional containing Admin if found
     */
    public Optional<Admin> findByEmail(String email) {
        String sql = "SELECT * FROM admin WHERE email = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapResultSetToAdmin(rs));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find admin by email", e);
        }
    }

    /**
     * Find admin by username.
     * @param username admin username
     * @return Optional containing Admin if found
     */
    public Optional<Admin> findByUsername(String username) {
        String sql = "SELECT * FROM admin WHERE username = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapResultSetToAdmin(rs));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find admin by username", e);
        }
    }

    // ---------- Helper method ----------

    /** Map a ResultSet row to an Admin object */
    private Admin mapResultSetToAdmin(ResultSet rs) throws SQLException {
        Admin admin = new Admin();
        admin.setId(UUID.fromString(rs.getString("id")));
        admin.setName(rs.getString("name"));
        admin.setUsername(rs.getString("username"));
        admin.setEmail(rs.getString("email"));
        admin.setPassword(rs.getString("password"));
        admin.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        admin.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return admin;
    }
}