package ch.unil.softarch.luxurycarrental.rest;

import ch.unil.softarch.luxurycarrental.domain.entities.Booking;
import ch.unil.softarch.luxurycarrental.service.BookingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Path("/bookings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookingResource {

    @Inject
    private BookingService bookingService;

    // ---------------- CRUD ----------------
    @GET
    public Collection<Booking> getAllBookings() {
        return bookingService.getAllBookings();
    }

    @GET
    @Path("/{bookingId}")
    public Booking getBooking(@PathParam("bookingId") UUID bookingId) {
        return bookingService.getBooking(bookingId);
    }

    @GET
    @Path("/customer/{customerId}")
    public List<Booking> getBookingsByCustomer(@PathParam("customerId") UUID customerId) {
        return bookingService.getBookingsByCustomerId(customerId);
    }

    @GET
    @Path("/car/{carId}")
    public List<Booking> getBookingsByCar(@PathParam("carId") UUID carId) {
        return bookingService.getBookingsByCarId(carId);
    }

    public static class BookingRequest {
        public UUID customerId;
        public UUID carId;
        public LocalDate startDate;
        public LocalDate endDate;
    }
    @POST
    public Response createBooking(BookingRequest request) {
        Booking booking = bookingService.createBooking(
                request.customerId,
                request.carId,
                request.startDate,
                request.endDate
        );
        return Response.ok(booking).build();
    }

    @PUT
    @Path("/{bookingId}")
    public Booking updateBooking(@PathParam("bookingId") UUID bookingId, Booking update) {
        return bookingService.updateBooking(bookingId, update);
    }

    @DELETE
    @Path("/{bookingId}")
    public Response removeBooking(@PathParam("bookingId") UUID bookingId) {
        boolean removed = bookingService.removeBooking(bookingId);
        if (removed) return Response.ok("Booking removed successfully").build();
        return Response.status(Response.Status.NOT_FOUND).entity("Booking not found").build();
    }

    // ---------------- Operations ----------------
    @POST
    @Path("/{bookingId}/pay")
    public Response payBooking(@PathParam("bookingId") UUID bookingId) {
        Booking booking = bookingService.payForBooking(bookingId);
        return Response.ok(booking).build();
    }

    @PUT
    @Path("/{bookingId}/complete")
    public Response completeBooking(@PathParam("bookingId") UUID bookingId) {
        bookingService.completeBooking(bookingId);
        return Response.ok("Booking completed successfully").build();
    }

    @PUT
    @Path("/{bookingId}/cancel")
    public Response cancelBooking(@PathParam("bookingId") UUID bookingId) {
        bookingService.cancelBooking(bookingId);
        return Response.ok("Booking canceled and deposit refunded").build();
    }

    @POST
    @Path("/{bookingId}/reject")
    public Response rejectBooking(@PathParam("bookingId") UUID bookingId, @QueryParam("reason") String reason) {
        Booking booking = bookingService.rejectBooking(bookingId, reason);
        return Response.ok(booking).build();
    }

    @PUT
    @Path("/{bookingId}/confirm")
    public Response confirmBooking(@PathParam("bookingId") UUID bookingId) {
        bookingService.confirmBooking(bookingId);
        return Response.ok( "Booking confirmed successfully").build();
    }
}