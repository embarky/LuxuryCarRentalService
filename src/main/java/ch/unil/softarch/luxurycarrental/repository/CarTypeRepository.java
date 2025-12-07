package ch.unil.softarch.luxurycarrental.repository;

import ch.unil.softarch.luxurycarrental.domain.entities.CarType;
import ch.unil.softarch.luxurycarrental.domain.enums.DriveType;
import ch.unil.softarch.luxurycarrental.domain.enums.Transmission;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.*;
import java.util.*;

@ApplicationScoped
public class CarTypeRepository implements CrudRepository<CarType> {

    // -------------------------------------
    // Save (INSERT + UPDATE)
    // -------------------------------------
    @Override
    public CarType save(CarType carType) {
        String sql = """
            INSERT INTO car_type
            (id, category, brand, model, engine, power, max_speed, acceleration, weight,
             drive_type, transmission, seats, description, features)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                category = VALUES(category),
                brand = VALUES(brand),
                model = VALUES(model),
                engine = VALUES(engine),
                power = VALUES(power),
                max_speed = VALUES(max_speed),
                acceleration = VALUES(acceleration),
                weight = VALUES(weight),
                drive_type = VALUES(drive_type),
                transmission = VALUES(transmission),
                seats = VALUES(seats),
                description = VALUES(description),
                features = VALUES(features)
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, carType.getId().toString());
            stmt.setString(2, carType.getCategory());
            stmt.setString(3, carType.getBrand());
            stmt.setString(4, carType.getModel());
            stmt.setString(5, carType.getEngine());
            stmt.setInt(6, carType.getPower());
            stmt.setInt(7, carType.getMaxSpeed());
            stmt.setDouble(8, carType.getAcceleration());
            stmt.setDouble(9, carType.getWeight());

            stmt.setString(10, carType.getDriveType() != null ? carType.getDriveType().name() : null);
            stmt.setString(11, carType.getTransmission() != null ? carType.getTransmission().name() : null);

            stmt.setInt(12, carType.getSeats());
            stmt.setString(13, carType.getDescription());

            // features list -> comma separated string
            stmt.setString(14, carType.getFeatures() != null
                    ? String.join(",", carType.getFeatures())
                    : null);

            stmt.executeUpdate();
            return carType;

        } catch (Exception e) {
            throw new RuntimeException("Failed to save CarType", e);
        }
    }

    // -------------------------------------
    // Find by ID
    // -------------------------------------
    @Override
    public Optional<CarType> findById(UUID id) {
        String sql = "SELECT * FROM car_type WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSetToCarType(rs));
            }
            return Optional.empty();

        } catch (Exception e) {
            throw new RuntimeException("Failed to find CarType by ID", e);
        }
    }

    // -------------------------------------
    // Find all
    // -------------------------------------
    @Override
    public List<CarType> findAll() {
        String sql = "SELECT * FROM car_type";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<CarType> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapResultSetToCarType(rs));
            }
            return list;

        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve all CarTypes", e);
        }
    }

    // -------------------------------------
    // Delete
    // -------------------------------------
    @Override
    public boolean delete(UUID id) {
        String sql = "DELETE FROM car_type WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete CarType", e);
        }
    }

    // -------------------------------------
    // Find by brand + model
    // -------------------------------------
    public Optional<CarType> findByBrandAndModel(String brand, String model) {
        String sql = "SELECT * FROM car_type WHERE brand = ? AND model = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, brand);
            stmt.setString(2, model);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSetToCarType(rs));
            }

            return Optional.empty();

        } catch (Exception e) {
            throw new RuntimeException("Failed to find CarType by brand and model", e);
        }
    }

    // -------------------------------------
    // Convert DB row into CarType object
    // -------------------------------------
    private CarType mapResultSetToCarType(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));

        // Convert features from "a,b,c" to List<String>
        List<String> features = new ArrayList<>();
        String rawFeatures = rs.getString("features");
        if (rawFeatures != null && !rawFeatures.isBlank()) {
            features = Arrays.asList(rawFeatures.split(","));
        }

        // Safe enum parsing
        DriveType driveType = null;
        String dt = rs.getString("drive_type");
        if (dt != null && !dt.isBlank()) {
            try { driveType = DriveType.valueOf(dt); } catch (Exception ignored) {}
        }

        Transmission transmission = null;
        String tm = rs.getString("transmission");
        if (tm != null && !tm.isBlank()) {
            try { transmission = Transmission.valueOf(tm); } catch (Exception ignored) {}
        }

        return new CarType(
                id,
                rs.getString("category"),
                rs.getString("brand"),
                rs.getString("model"),
                rs.getString("engine"),
                rs.getInt("power"),
                rs.getInt("max_speed"),
                rs.getDouble("acceleration"),
                rs.getDouble("weight"),
                driveType,
                transmission,
                rs.getInt("seats"),
                rs.getString("description"),
                features
        );
    }
}