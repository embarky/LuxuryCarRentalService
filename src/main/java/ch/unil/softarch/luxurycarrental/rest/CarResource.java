package ch.unil.softarch.luxurycarrental.rest;

import ch.unil.softarch.luxurycarrental.domain.entities.Car;
import ch.unil.softarch.luxurycarrental.service.CarService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * REST resource class for managing Car entities.
 * Handles CRUD operations through HTTP requests.
 */
@Path("/cars")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CarResource {

    @Inject
    private CarService carService;

    /**
     * Retrieve all cars.
     * @return a list of all available cars
     */
    @GET
    public List<Car> getAllCars() {
        return carService.getAllCars();
    }

    /**
     * Retrieve a specific car by its ID.
     * @param id the unique identifier of the car
     * @return the car with the given ID
     */
    @GET
    @Path("/{id}")
    public Car getCar(@PathParam("id") UUID id) {
        return carService.getCar(id);
    }

    /**
     * Create a new car entry.
     * @param car the car object to be added
     * @return the created car with its generated ID
     */
    @POST
    public Car addCar(Car car) {
        return carService.addCar(car);
    }

    /**
     * Update an existing car's information.
     * Only non-null or positive fields will be updated.
     * @param id the ID of the car to update
     * @param update the new car data
     * @return the updated car object
     */
    @PUT
    @Path("/{id}")
    public Car updateCar(@PathParam("id") UUID id, Car update) {
        return carService.updateCar(id, update);
    }

    /**
     * Delete a car by its ID.
     * @param id the unique identifier of the car
     * @return true if deletion was successful, false otherwise
     */
    @DELETE
    @Path("/{id}")
    public boolean removeCar(@PathParam("id") UUID id) {
        return carService.removeCar(id);
    }

    /**
     * Fuzzy search / filter cars.
     * Example query:
     * GET /cars/search?q=camry&maxPrice=150&status=AVAILABLE
     */
    @GET
    @Path("/search")
    public List<Car> searchCars(
            @QueryParam("q") String q,
            @QueryParam("status") String status,
            @QueryParam("minPrice") Double minPrice,
            @QueryParam("maxPrice") Double maxPrice,
            @QueryParam("carTypeId") String carTypeIdStr  // accept as string and convert
    ) {
        UUID carTypeId = null;
        if (carTypeIdStr != null && !carTypeIdStr.isBlank()) {
            try {
                carTypeId = UUID.fromString(carTypeIdStr);
            } catch (IllegalArgumentException e) {
                throw new WebApplicationException("Invalid carTypeId UUID", 400);
            }
        }

        return carService.searchCars(q, status, minPrice, maxPrice, carTypeId);
    }
}