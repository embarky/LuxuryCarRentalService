package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.CarType;
import ch.unil.softarch.luxurycarrental.repository.CarRepository;
import ch.unil.softarch.luxurycarrental.repository.CarTypeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CarTypeService {

    @Inject
    private CarTypeRepository carTypeRepo; // JDBC repository for CarType

    @Inject
    private CarRepository carRepo; // JDBC repository for Car (for usage check)

    // ---------- CREATE ----------
    public CarType addCarType(CarType carType) {
        if (carType.getId() == null) {
            carType.setId(UUID.randomUUID());
        }

        // Optional: check for duplicate brand + model
        Optional<CarType> existing = carTypeRepo.findByBrandAndModel(carType.getBrand(), carType.getModel());
        if (existing.isPresent()) {
            throw new WebApplicationException("CarType with this brand and model already exists", 400);
        }

        return carTypeRepo.save(carType);
    }

    // ---------- READ ----------
    public CarType getCarType(UUID id) {
        return carTypeRepo.findById(id)
                .orElseThrow(() -> new WebApplicationException("CarType not found", 404));
    }

    public List<CarType> getAllCarTypes() {
        return carTypeRepo.findAll();
    }

    // ---------- UPDATE ----------
    public CarType updateCarType(UUID id, CarType update) {
        CarType existing = getCarType(id);

        if (update.getCategory() != null) existing.setCategory(update.getCategory());
        if (update.getBrand() != null) existing.setBrand(update.getBrand());
        if (update.getModel() != null) existing.setModel(update.getModel());
        if (update.getEngine() != null) existing.setEngine(update.getEngine());
        if (update.getPower() > 0) existing.setPower(update.getPower());
        if (update.getMaxSpeed() > 0) existing.setMaxSpeed(update.getMaxSpeed());
        if (update.getAcceleration() > 0) existing.setAcceleration(update.getAcceleration());
        if (update.getWeight() > 0) existing.setWeight(update.getWeight());
        if (update.getDriveType() != null) existing.setDriveType(update.getDriveType());
        if (update.getTransmission() != null) existing.setTransmission(update.getTransmission());
        if (update.getSeats() > 0) existing.setSeats(update.getSeats());
        if (update.getDescription() != null) existing.setDescription(update.getDescription());
        if (update.getFeatures() != null) existing.setFeatures(update.getFeatures());

        return carTypeRepo.save(existing);
    }

    // ---------- DELETE ----------
    public boolean removeCarType(UUID id) {
        CarType existing = getCarType(id);

        // Check if there is a Car using this CarType
        boolean inUseByCars = carRepo.findAll().stream()
                .anyMatch(car -> car.getCarType() != null && id.equals(car.getCarType().getId()));

        if (inUseByCars) {
            throw new WebApplicationException(
                    "Cannot delete CarType: there are cars still using this type", 400);
        }

        return carTypeRepo.delete(id);
    }

    // ---------- CUSTOM QUERY ----------
    public CarType getByBrandAndModel(String brand, String model) {
        return carTypeRepo.findByBrandAndModel(brand, model)
                .orElseThrow(() -> new WebApplicationException("CarType not found for brand and model", 404));
    }
}
