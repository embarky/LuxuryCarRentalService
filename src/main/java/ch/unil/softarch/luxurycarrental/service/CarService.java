package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.Car;
import ch.unil.softarch.luxurycarrental.domain.entities.CarType;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.CarStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service class for managing Cars.
 * <p>
 * Handles CRUD operations and complex search queries using JPA.
 * Ensures data consistency by validating CarType relationships and Booking dependencies.
 * </p>
 */
@ApplicationScoped
public class CarService {

    @PersistenceContext(unitName = "LuxuryCarRentalPU")
    private EntityManager em;

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Retrieves a car by its unique ID.
     */
    public Car getCar(UUID id) {
        Car car = em.find(Car.class, id);
        if (car == null) throw new WebApplicationException("Car not found", 404);
        return car;
    }

    /**
     * Retrieves all cars in the database.
     */
    public List<Car> getAllCars() {
        return em.createQuery("SELECT c FROM Car c", Car.class).getResultList();
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Adds a new car to the fleet.
     * Validates that the associated CarType exists.
     */
    @Transactional
    public Car addCar(Car car) {
        // Validate CarType input
        if (car.getCarType() == null || car.getCarType().getId() == null) {
            throw new WebApplicationException("CarType must be provided", 400);
        }

        // Check if CarType exists in the database
        CarType type = em.find(CarType.class, car.getCarType().getId());
        if (type == null) {
            throw new WebApplicationException("CarType does not exist", 400);
        }

        // Link the managed CarType entity to the Car
        car.setCarType(type);

        // ID and Timestamps are handled by @PrePersist in the entity
        em.persist(car);
        return car;
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Updates an existing car's details.
     */
    @Transactional
    public Car updateCar(UUID id, Car update) {
        Car existing = em.find(Car.class, id);
        if (existing == null) throw new WebApplicationException("Car not found", 404);

        // Handle CarType update
        if (update.getCarType() != null && update.getCarType().getId() != null) {
            CarType newType = em.find(CarType.class, update.getCarType().getId());
            if (newType == null) {
                throw new WebApplicationException("CarType does not exist", 400);
            }
            existing.setCarType(newType);
        }

        // Update basic fields
        if (update.getLicensePlate() != null) existing.setLicensePlate(update.getLicensePlate());
        if (update.getDailyRentalPrice() > 0) existing.setDailyRentalPrice(update.getDailyRentalPrice());
        if (update.getDepositAmount() > 0) existing.setDepositAmount(update.getDepositAmount());
        if (update.getStatus() != null) existing.setStatus(update.getStatus());
        if (update.getImageUrl() != null) existing.setImageUrl(update.getImageUrl());
        if (update.getRegistrationDate() != null) existing.setRegistrationDate(update.getRegistrationDate());
        if (update.getLastMaintenanceDate() != null) existing.setLastMaintenanceDate(update.getLastMaintenanceDate());
        if (update.getVin() != null) existing.setVin(update.getVin());
        if (update.getColor() != null) existing.setColor(update.getColor());
        if (update.getInsuranceExpiryDate() != null) existing.setInsuranceExpiryDate(update.getInsuranceExpiryDate());

        // Changes are automatically flushed to DB at the end of transaction
        return existing;
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Removes a car from the fleet.
     * Prevents deletion if the car has active bookings.
     */
    @Transactional
    public boolean removeCar(UUID id) {
        Car existing = em.find(Car.class, id);
        if (existing == null) {
            throw new WebApplicationException("Car not found", 404);
        }

        // --- Efficient Dependency Check using JP-QL ---
        // Instead of loading all bookings, we count how many active bookings reference this car.
        String jpql = "SELECT COUNT(b) FROM Booking b WHERE b.car.id = :carId " +
                "AND b.bookingStatus IN :statuses";

        Long activeBookingsCount = em.createQuery(jpql, Long.class)
                .setParameter("carId", id)
                .setParameter("statuses", List.of(
                        BookingStatus.PENDING,
                        BookingStatus.CONFIRMED,
                        BookingStatus.COMPLETED))
                .getSingleResult();

        if (activeBookingsCount > 0) {
            throw new WebApplicationException(
                    "Cannot delete Car: there are existing bookings linked to this car", 400);
        }

        // --- Safe to delete ---
        em.remove(existing);
        return true;
    }

    // -------------------------------------------------------------------------
    // Search (Advanced)
    // -------------------------------------------------------------------------

    /**
     * Searches for cars using dynamic criteria (Fuzzy Search + Filters).
     *
     * @param q          Fuzzy search term (matches brand, model, license, color, etc.)
     * @param status     Filter by CarStatus (e.g., "AVAILABLE")
     * @param minPrice   Filter by minimum daily price
     * @param maxPrice   Filter by maximum daily price
     * @param carTypeId  Filter by specific CarType ID
     * @return List of cars matching the criteria
     */
    public List<Car> searchCars(String q, String status, Double minPrice, Double maxPrice, UUID carTypeId) {
        // Start building the JP-QL query
        // We JOIN 'carType' to allow searching/filtering by its fields
        StringBuilder queryBuilder = new StringBuilder("SELECT c FROM Car c JOIN c.carType ct WHERE 1=1");

        // List to hold parameters to avoid SQL injection
        List<Object> params = new ArrayList<>();
        // Helper list to map parameter names to values (for named parameters) is tricky with dynamic builder
        // So we will use positional parameters for simplicity ?1, ?2 etc.
        // OR better: use a distinct logic for setting params after building.

        // Let's use a cleaner approach with logic to set params later
        boolean hasStatus = false;
        boolean hasMinPrice = false;
        boolean hasMaxPrice = false;
        boolean hasCarType = false;
        boolean hasTerm = false;

        // 1. Status Filter
        CarStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = CarStatus.valueOf(status.toUpperCase());
                queryBuilder.append(" AND c.status = :status");
                hasStatus = true;
            } catch (IllegalArgumentException ignored) {
                // If invalid status string provided, ignore filter (same as original logic)
            }
        }

        // 2. Price Range Filters
        if (minPrice != null) {
            queryBuilder.append(" AND c.dailyRentalPrice >= :minPrice");
            hasMinPrice = true;
        }
        if (maxPrice != null) {
            queryBuilder.append(" AND c.dailyRentalPrice <= :maxPrice");
            hasMaxPrice = true;
        }

        // 3. CarType Filter
        if (carTypeId != null) {
            queryBuilder.append(" AND ct.id = :carTypeId");
            hasCarType = true;
        }

        // 4. Fuzzy Search (Term)
        String term = null;
        if (q != null && !q.isBlank()) {
            term = "%" + q.trim().toLowerCase() + "%"; // Prepare for LIKE '%term%'
            queryBuilder.append(" AND (")
                    .append("LOWER(c.licensePlate) LIKE :term OR ")
                    .append("LOWER(c.vin) LIKE :term OR ")
                    .append("LOWER(c.color) LIKE :term OR ")
                    .append("LOWER(ct.brand) LIKE :term OR ")
                    .append("LOWER(ct.model) LIKE :term OR ")
                    .append("LOWER(ct.category) LIKE :term")
                    .append(")");
            hasTerm = true;
        }

        // Create Query
        TypedQuery<Car> query = em.createQuery(queryBuilder.toString(), Car.class);

        // Set Parameters
        if (hasStatus) query.setParameter("status", statusEnum);
        if (hasMinPrice) query.setParameter("minPrice", minPrice);
        if (hasMaxPrice) query.setParameter("maxPrice", maxPrice);
        if (hasCarType) query.setParameter("carTypeId", carTypeId);
        if (hasTerm) query.setParameter("term", term);

        return query.getResultList();
    }
}