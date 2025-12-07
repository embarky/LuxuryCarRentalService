package ch.unil.softarch.luxurycarrental.repository;

import ch.unil.softarch.luxurycarrental.domain.entities.Car;
import ch.unil.softarch.luxurycarrental.domain.entities.CarType;
import ch.unil.softarch.luxurycarrental.domain.enums.CarStatus;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Repository for Car entity using JDBC.
 */
@ApplicationScoped
public class CarRepository implements CrudRepository<Car> {

    private final CarTypeRepository carTypeRepo = new CarTypeRepository();

    // ---------- CREATE / UPDATE ----------
    @Override
    public Car save(Car car) {
        String sql = """
            INSERT INTO car
            (id, license_plate, car_type_id, daily_rental_price, deposit_amount, status, image_url,
             registration_date, last_maintenance_date, vin, color, insurance_expiry_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                license_plate = VALUES(license_plate),
                car_type_id = VALUES(car_type_id),
                daily_rental_price = VALUES(daily_rental_price),
                deposit_amount = VALUES(deposit_amount),
                status = VALUES(status),
                image_url = VALUES(image_url),
                registration_date = VALUES(registration_date),
                last_maintenance_date = VALUES(last_maintenance_date),
                vin = VALUES(vin),
                color = VALUES(color),
                insurance_expiry_date = VALUES(insurance_expiry_date)
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, car.getId().toString());
            stmt.setString(2, car.getLicensePlate());
            stmt.setString(3, car.getCarType().getId().toString());
            stmt.setDouble(4, car.getDailyRentalPrice());
            stmt.setDouble(5, car.getDepositAmount());
            stmt.setString(6, car.getStatus().name());
            stmt.setString(7, car.getImageUrl());
            stmt.setDate(8, Date.valueOf(car.getRegistrationDate()));
            stmt.setDate(9, Date.valueOf(car.getLastMaintenanceDate()));
            stmt.setString(10, car.getVin());
            stmt.setString(11, car.getColor());
            stmt.setDate(12, Date.valueOf(car.getInsuranceExpiryDate()));

            stmt.executeUpdate();
            return car;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save car", e);
        }
    }

    // ---------- READ ----------
    @Override
    public Optional<Car> findById(UUID id) {
        String sql = "SELECT * FROM car WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return Optional.of(mapResultSetToCar(rs, conn));
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find car", e);
        }
    }

    @Override
    public List<Car> findAll() {
        String sql = "SELECT * FROM car";
        List<Car> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToCar(rs, conn));
            }
            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to list cars", e);
        }
    }

    // ---------- DELETE ----------
    @Override
    public boolean delete(UUID id) {
        String sql = "DELETE FROM car WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete car", e);
        }
    }

    // ---------- CUSTOM METHODS ----------
    /**
     * Check if the car has active bookings.
     */
    public boolean hasActiveBookings(UUID carId) {
        String sql = """
            SELECT COUNT(*) FROM booking 
            WHERE car_id = ? AND booking_status IN ('PENDING','CONFIRMED','COMPLETED')
        """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, carId.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1) > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check active bookings", e);
        }
        return false;
    }

    /**
     * Search cars with optional filters.
     */
    public List<Car> search(String q, String status, Double minPrice, Double maxPrice, UUID carTypeId) {
        List<Car> allCars = findAll();
        String term = (q == null || q.isBlank()) ? null : q.toLowerCase().trim();

        return allCars.stream().filter(car -> {
            if (status != null && !status.isBlank() && !car.getStatus().name().equalsIgnoreCase(status)) return false;
            if (minPrice != null && car.getDailyRentalPrice() < minPrice) return false;
            if (maxPrice != null && car.getDailyRentalPrice() > maxPrice) return false;
            if (carTypeId != null && (car.getCarType() == null || !carTypeId.equals(car.getCarType().getId()))) return false;
            if (term == null) return true;

            // fuzzy search
            if (car.getLicensePlate() != null && car.getLicensePlate().toLowerCase().contains(term)) return true;
            if (car.getVin() != null && car.getVin().toLowerCase().contains(term)) return true;
            if (car.getColor() != null && car.getColor().toLowerCase().contains(term)) return true;
            CarType ct = car.getCarType();
            if (ct != null) {
                if (ct.getBrand() != null && ct.getBrand().toLowerCase().contains(term)) return true;
                if (ct.getModel() != null && ct.getModel().toLowerCase().contains(term)) return true;
                if (ct.getCategory() != null && ct.getCategory().toLowerCase().contains(term)) return true;
            }
            return String.valueOf(car.getDailyRentalPrice()).contains(term);
        }).toList();
    }

    // ---------- HELPER ----------
    private Car mapResultSetToCar(ResultSet rs, Connection conn) throws SQLException {
        UUID carTypeId = UUID.fromString(rs.getString("car_type_id"));
        CarType type = carTypeRepo.findById(carTypeId).orElse(null);

        return new Car(
                UUID.fromString(rs.getString("id")),
                rs.getString("license_plate"),
                type,
                rs.getDouble("daily_rental_price"),
                rs.getDouble("deposit_amount"),
                CarStatus.valueOf(rs.getString("status")),
                rs.getString("image_url"),
                rs.getDate("registration_date").toLocalDate(),
                rs.getDate("last_maintenance_date").toLocalDate(),
                rs.getString("vin"),
                rs.getString("color"),
                rs.getDate("insurance_expiry_date").toLocalDate()
        );
    }
}