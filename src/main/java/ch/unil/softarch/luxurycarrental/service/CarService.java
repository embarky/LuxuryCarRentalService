package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.ApplicationState;
import ch.unil.softarch.luxurycarrental.domain.entities.Car;
import ch.unil.softarch.luxurycarrental.domain.entities.CarType;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class CarService {

    @Inject
    private ApplicationState state;

    // Read
    public Car getCar(UUID id) {
        Car car = state.getCars().get(id);
        if (car == null) throw new WebApplicationException("Car not found", 404); // ✅ 一致的异常处理
        return car;
    }

    public List<Car> getAllCars() {
        return new ArrayList<>(state.getCars().values());
    }

    // Create
    public Car addCar(Car car) {
        if (car.getCarType() == null || car.getCarType().getId() == null) {
            throw new WebApplicationException("CarType must be provided", 400);
        }
        // Check that the CarType exists
        if (!state.getCarTypes().containsKey(car.getCarType().getId())) {
            throw new WebApplicationException("CarType does not exist", 400);
        }

        if (car.getId() == null) car.setId(UUID.randomUUID());
        state.getCars().put(car.getId(), car);
        return car;
    }

    // Update
    public Car updateCar(UUID id, Car update) {
        Car existing = state.getCars().get(id);
        if (existing == null) throw new WebApplicationException("Car not found", 404);

        // If updating CarType, check it exists
        if (update.getCarType() != null) {
            if (update.getCarType().getId() == null ||
                    !state.getCarTypes().containsKey(update.getCarType().getId())) {
                throw new WebApplicationException("CarType does not exist", 400);
            }
            existing.setCarType(update.getCarType());
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

        return existing;
    }

    // Delete a Car by its ID
    public boolean removeCar(UUID id) {
        Car existing = state.getCars().get(id);
        if (existing == null) {
            throw new WebApplicationException("Car not found", 404);
        }

        // --- Check if there are active bookings related to this car ---
        boolean hasActiveBookings = state.getBookings().values().stream().anyMatch(booking ->
                booking.getCar() != null &&
                        id.equals(booking.getCar().getId()) &&
                        booking.getBookingStatus() != null &&
                        (booking.getBookingStatus() == BookingStatus.PENDING ||
                                booking.getBookingStatus() == BookingStatus.CONFIRMED ||
                                booking.getBookingStatus() == BookingStatus.COMPLETED)
        );

        if (hasActiveBookings) {
            throw new WebApplicationException(
                    "Cannot delete Car: there are existing bookings linked to this car", 400);
        }

        // --- Safe to delete if no bookings depend on this car ---
        return state.getCars().remove(id) != null;
    }

    public List<Car> searchCars(
            String q,                // main fuzzy query, matches brand/model/licensePlate/vin/color/carType fields
            String status,           // optional car status filter (e.g. "AVAILABLE")
            Double minPrice,         // optional min dailyRentalPrice
            Double maxPrice,         // optional max dailyRentalPrice
            UUID carTypeId           // optional exact carType id filter
    ) {
        // normalize search term
        String term = (q == null || q.isBlank()) ? null : q.trim().toLowerCase();

        return state.getCars().values().stream()
                .filter(car -> {
                    // filter by status if provided
                    if (status != null && !status.isBlank()) {
                        try {
                            if (!car.getStatus().name().equalsIgnoreCase(status)) return false;
                        } catch (Exception ignored) { return false; }
                    }

                    // filter by price range
                    if (minPrice != null && car.getDailyRentalPrice() < minPrice) return false;
                    if (maxPrice != null && car.getDailyRentalPrice() > maxPrice) return false;

                    // filter by carType id if provided
                    if (carTypeId != null) {
                        if (car.getCarType() == null || !carTypeId.equals(car.getCarType().getId())) return false;
                    }

                    // if no fuzzy term provided, keep (already passed other filters)
                    if (term == null) return true;

                    // match against multiple fields (fuzzy: contains, case-insensitive)
                    if (car.getLicensePlate() != null && car.getLicensePlate().toLowerCase().contains(term)) return true;
                    if (car.getVin() != null && car.getVin().toLowerCase().contains(term)) return true;
                    if (car.getColor() != null && car.getColor().toLowerCase().contains(term)) return true;

                    // match carType fields if present
                    CarType ct = car.getCarType();
                    if (ct != null) {
                        if (ct.getBrand() != null && ct.getBrand().toLowerCase().contains(term)) return true;
                        if (ct.getModel() != null && ct.getModel().toLowerCase().contains(term)) return true;
                        if (ct.getCategory() != null && ct.getCategory().toLowerCase().contains(term)) return true;
                    }

                    // match numeric fields stringified (e.g., power, seats) — optional convenience
                    if (String.valueOf(car.getDailyRentalPrice()).contains(term)) return true;
                    if (car.getId() != null && car.getId().toString().toLowerCase().contains(term)) return true;

                    return false;
                })
                .collect(Collectors.toList());
    }
}