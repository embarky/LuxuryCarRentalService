package ch.unil.softarch.luxurycarrental.repository;

import ch.unil.softarch.luxurycarrental.domain.entities.Booking;
import ch.unil.softarch.luxurycarrental.domain.entities.Car;
import ch.unil.softarch.luxurycarrental.domain.entities.Customer;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class BookingRepository implements CrudRepository<Booking> {

    @Inject
    private CarRepository carRepo;

    @Inject
    private CustomerRepository customerRepo;

    /**
     * Inserts or updates a booking in the database.
     * Supports both MySQL and PostgreSQL UPSERT syntax.
     */
    @Override
    public Booking save(Booking booking) {
        String dbProduct;
        try (Connection conn = DBConnection.getConnection()) {
            dbProduct = conn.getMetaData().getDatabaseProductName().toLowerCase();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to detect database type", e);
        }

        String sql;

        // PostgreSQL UPSERT
        if (dbProduct.contains("postgres")) {
            sql = """
                    INSERT INTO booking
                    (booking_id, car_id, customer_id, start_date, end_date,
                     total_cost, deposit_amount, booking_status, payment_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (booking_id) DO UPDATE SET
                        car_id = EXCLUDED.car_id,
                        customer_id = EXCLUDED.customer_id,
                        start_date = EXCLUDED.start_date,
                        end_date = EXCLUDED.end_date,
                        total_cost = EXCLUDED.total_cost,
                        deposit_amount = EXCLUDED.deposit_amount,
                        booking_status = EXCLUDED.booking_status,
                        payment_status = EXCLUDED.payment_status;
                """;
        }
        // MySQL UPSERT
        else {
            sql = """
                    INSERT INTO booking
                    (booking_id, car_id, customer_id, start_date, end_date,
                     total_cost, deposit_amount, booking_status, payment_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        car_id = VALUES(car_id),
                        customer_id = VALUES(customer_id),
                        start_date = VALUES(start_date),
                        end_date = VALUES(end_date),
                        total_cost = VALUES(total_cost),
                        deposit_amount = VALUES(deposit_amount),
                        booking_status = VALUES(booking_status),
                        payment_status = VALUES(payment_status);
                """;
        }

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, booking.getBookingId().toString());
            stmt.setString(2, booking.getCar().getId().toString());
            stmt.setString(3, booking.getCustomer().getId().toString());
            stmt.setDate(4, Date.valueOf(booking.getStartDate()));
            stmt.setDate(5, Date.valueOf(booking.getEndDate()));
            stmt.setDouble(6, booking.getTotalCost());
            stmt.setDouble(7, booking.getDepositAmount());
            stmt.setString(8, booking.getBookingStatus().name());
            stmt.setString(9, booking.getPaymentStatus().name());

            stmt.executeUpdate();
            return booking;

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to save booking", e);
        }
    }

    /**
     * Find a booking by ID.
     */
    @Override
    public Optional<Booking> findById(UUID id) {
        String sql = "SELECT * FROM booking WHERE booking_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToBooking(rs));
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find booking", e);
        }
    }

    /**
     * Retrieve all bookings.
     */
    @Override
    public List<Booking> findAll() {
        String sql = "SELECT * FROM booking";
        List<Booking> list = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                list.add(mapRowToBooking(rs));
            }

            return list;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch bookings", e);
        }
    }

    /**
     * Delete booking by ID.
     */
    @Override
    public boolean delete(UUID id) {
        String sql = "DELETE FROM booking WHERE booking_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id.toString());
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete booking", e);
        }
    }

    /**
     * Helper method to convert a ResultSet row into a Booking object.
     * Loads associated Car and Customer entities.
     */
    private Booking mapRowToBooking(ResultSet rs) throws SQLException {
        UUID bookingId = UUID.fromString(rs.getString("booking_id"));
        UUID carId = UUID.fromString(rs.getString("car_id"));
        UUID customerId = UUID.fromString(rs.getString("customer_id"));

        Car car = carRepo.findById(carId).orElse(null);
        Customer customer = customerRepo.findById(customerId).orElse(null);

        LocalDate start = rs.getDate("start_date").toLocalDate();
        LocalDate end = rs.getDate("end_date").toLocalDate();

        return new Booking(
                bookingId,
                car,
                customer,
                start,
                end,
                rs.getDouble("total_cost"),
                rs.getDouble("deposit_amount"),
                BookingStatus.valueOf(rs.getString("booking_status")),
                PaymentStatus.valueOf(rs.getString("payment_status"))
        );
    }
}