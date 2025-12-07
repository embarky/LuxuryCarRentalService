package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.Car;
import ch.unil.softarch.luxurycarrental.domain.entities.CarType;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.repository.CarRepository;
import ch.unil.softarch.luxurycarrental.repository.CarTypeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CarService {

    @Inject
    private CarRepository carRepo; // JDBC repository for Car

    @Inject
    private CarTypeRepository carTypeRepo; // JDBC repository for CarType

    // ---------- CREATE ----------
    /**
     * Add a new Car to the database.
     * Checks that the associated CarType exists.
     * @param car Car object to add
     * @return saved Car object
     */
    public Car addCar(Car car) {
        if (car.getCarType() == null || car.getCarType().getId() == null) {
            throw new WebApplicationException("CarType must be provided", 400);
        }

        CarType ct = carTypeRepo.findById(car.getCarType().getId())
                .orElseThrow(() -> new WebApplicationException("CarType does not exist", 400));
        car.setCarType(ct);

        if (car.getId() == null) car.setId(UUID.randomUUID());
        return carRepo.save(car);
    }

    // ---------- READ ----------
    /**
     * Get a Car by its ID.
     * @param id Car UUID
     * @return Car object
     */
    public Car getCar(UUID id) {
        return carRepo.findById(id)
                .orElseThrow(() -> new WebApplicationException("Car not found", 404));
    }

    /**
     * Get all Cars.
     * @return list of Car objects
     */
    public List<Car> getAllCars() {
        return carRepo.findAll();
    }

    // ---------- UPDATE ----------
    /**
     * Update a Car.
     * If CarType is updated, check it exists.
     * @param id Car UUID
     * @param update Car object with updated fields
     * @return updated Car object
     */
    public Car updateCar(UUID id, Car update) {
        Car existing = getCar(id);

        if (update.getCarType() != null) {
            CarType ct = carTypeRepo.findById(update.getCarType().getId())
                    .orElseThrow(() -> new WebApplicationException("CarType does not exist", 400));
            existing.setCarType(ct);
        }

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

        return carRepo.save(existing);
    }

    // ---------- DELETE ----------
    /**
     * Delete a Car by its ID.
     * Ensures no active bookings depend on this car.
     * @param id Car UUID
     * @return true if deleted successfully
     */
    public boolean removeCar(UUID id) {
        Car car = getCar(id);

        // Check active bookings
        boolean hasActiveBookings = carRepo.hasActiveBookings(id);
        if (hasActiveBookings) {
            throw new WebApplicationException(
                    "Cannot delete Car: there are existing bookings linked to this car", 400);
        }

        return carRepo.delete(id);
    }

    // ---------- SEARCH ----------
    /**
     * Search cars by fuzzy query, status, price range, or carType.
     * @param q main fuzzy query
     * @param status optional car status filter
     * @param minPrice optional min daily rental price
     * @param maxPrice optional max daily rental price
     * @param carTypeId optional carType id
     * @return filtered list of cars
     */
    public List<Car> searchCars(String q, String status, Double minPrice, Double maxPrice, UUID carTypeId) {
        return carRepo.search(q, status, minPrice, maxPrice, carTypeId);
    }
}