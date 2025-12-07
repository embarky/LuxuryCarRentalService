package ch.unil.softarch.luxurycarrental.repository;

import ch.unil.softarch.luxurycarrental.domain.entities.Customer;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Repository for Customer entity using JDBC to persist in the database.
 */
@ApplicationScoped
public class CustomerRepository implements CrudRepository<Customer> {

    /**
     * Save or update a customer in the database.
     * @param customer Customer object to save or update
     * @return saved Customer object
     */
    @Override
    public Customer save(Customer customer) {
        String sql = """
            INSERT INTO customer
            (id, first_name, last_name, email, password, phone_number, driving_license_number,
             driving_license_expiry_date, age, verified_identity, billing_address, balance, creation_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                first_name = VALUES(first_name),
                last_name = VALUES(last_name),
                email = VALUES(email),
                password = VALUES(password),
                phone_number = VALUES(phone_number),
                driving_license_number = VALUES(driving_license_number),
                driving_license_expiry_date = VALUES(driving_license_expiry_date),
                age = VALUES(age),
                verified_identity = VALUES(verified_identity),
                billing_address = VALUES(billing_address),
                balance = VALUES(balance)
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, customer.getId().toString());
            stmt.setString(2, customer.getFirstName());
            stmt.setString(3, customer.getLastName());
            stmt.setString(4, customer.getEmail());
            stmt.setString(5, customer.getPassword());
            stmt.setString(6, customer.getPhoneNumber());
            stmt.setString(7, customer.getDrivingLicenseNumber());
            stmt.setDate(8, customer.getDrivingLicenseExpiryDate() != null ? new java.sql.Date(customer.getDrivingLicenseExpiryDate().getTime()) : null);
            stmt.setInt(9, customer.getAge());
            stmt.setBoolean(10, customer.isVerifiedIdentity());
            stmt.setString(11, customer.getBillingAddress());
            stmt.setDouble(12, customer.getBalance());
            stmt.setTimestamp(13, Timestamp.valueOf(customer.getCreationDate()));

            stmt.executeUpdate();
            return customer;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save customer", e);
        }
    }

    /**
     * Find a customer by ID.
     */
    @Override
    public Optional<Customer> findById(UUID id) {
        String sql = "SELECT * FROM customer WHERE id = ?";
        return findByField(id.toString(), sql, true);
    }

    /**
     * Find all customers.
     */
    @Override
    public List<Customer> findAll() {
        String sql = "SELECT * FROM customer";
        List<Customer> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToCustomer(rs));
            }
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to list customers", e);
        }
    }

    /**
     * Delete a customer by ID.
     */
    @Override
    public boolean delete(UUID id) {
        String sql = "DELETE FROM customer WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete customer", e);
        }
    }

    // ---------- Custom finders ----------

    /** Find customer by email */
    public Optional<Customer> findByEmail(String email) {
        String sql = "SELECT * FROM customer WHERE email = ?";
        return findByField(email, sql, false);
    }

    /** Find customer by phone number */
    public Optional<Customer> findByPhoneNumber(String phone) {
        String sql = "SELECT * FROM customer WHERE phone_number = ?";
        return findByField(phone, sql, false);
    }

    /** Find customer by driving license number */
    public Optional<Customer> findByDrivingLicenseNumber(String license) {
        String sql = "SELECT * FROM customer WHERE driving_license_number = ?";
        return findByField(license, sql, false);
    }

    // ---------- Helper methods ----------

    /** Helper to find a customer by a single field (ID or other) */
    private Optional<Customer> findByField(String value, String sql, boolean isId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, value);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToCustomer(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find customer by field", e);
        }
    }

    /** Map a ResultSet row to a Customer object */
    private Customer mapResultSetToCustomer(ResultSet rs) throws SQLException {
        return new Customer(
                UUID.fromString(rs.getString("id")),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("password"),
                rs.getString("phone_number"),
                rs.getString("driving_license_number"),
                rs.getDate("driving_license_expiry_date"),
                rs.getInt("age"),
                rs.getBoolean("verified_identity"),
                rs.getString("billing_address"),
                rs.getDouble("balance"),
                rs.getTimestamp("creation_date").toLocalDateTime()
        );
    }
}