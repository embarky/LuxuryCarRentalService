package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.ApplicationState;
import ch.unil.softarch.luxurycarrental.domain.entities.*;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.CarStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.io.ByteArrayOutputStream;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDate;

@ApplicationScoped
public class BookingService {

    @Inject
    private ApplicationState state;

    // ---------------- CRUD ----------------
    public Collection<Booking> getAllBookings() {
        return state.getBookings().values();
    }

    public Booking getBooking(UUID id) {
        return state.getBookings().get(id);
    }

    // Get bookings by customer ID
    public List<Booking> getBookingsByCustomerId(UUID customerId) {
        return state.getBookings().values().stream()
                .filter(b -> b.getCustomer() != null && b.getCustomer().getId().equals(customerId))
                .collect(Collectors.toList());
    }

    // Get bookings by car ID
    public List<Booking> getBookingsByCarId(UUID carId) {
        return state.getBookings().values().stream()
                .filter(b -> b.getCar() != null && b.getCar().getId().equals(carId))
                .collect(Collectors.toList());
    }

    public Booking createBooking(UUID customerId, UUID carId,
                                 LocalDate startDate, LocalDate endDate) {

        // --- Retrieve entities by ID ---
        Customer customer = state.getCustomers().get(customerId);
        Car car = state.getCars().get(carId);

        if (customer == null) throw new WebApplicationException("Customer does not exist", 400);
        if (car == null) throw new WebApplicationException("Car does not exist", 400);
        if (car.getStatus() != CarStatus.AVAILABLE) throw new WebApplicationException("Car is not available", 400);
        if (startDate == null || endDate == null)
            throw new WebApplicationException("Start date and end date are required", 400);
        if (endDate.isBefore(startDate))
            throw new WebApplicationException("End date must be after start date", 400);

        // --- Calculate total cost and deposit based on car's own values ---
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1; // inclusive
        double totalCost = car.getDailyRentalPrice() * days;
        double depositAmount = car.getDepositAmount();

        // --- Create booking ---
        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setCar(car);
        booking.setStartDate(startDate);
        booking.setEndDate(endDate);
        booking.setDepositAmount(depositAmount);
        booking.setTotalCost(totalCost);
        booking.setBookingStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.PENDING);

        state.getBookings().put(booking.getBookingId(), booking);

        // --- Prepare email and PDF ---
        String htmlBody = "<h2>Booking Created</h2>" +
                "<p>Hi <b>" + customer.getFirstName() + " " + customer.getLastName() + "</b>,</p>" +
                "<p>Your booking has been <b>created</b> successfully! It is now <b>pending confirmation and payment</b>. Here are the details:</p>";

        byte[] pdfBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc);
            doc.add(new Paragraph("Booking Confirmation"));
            doc.add(new Paragraph("Customer: " + customer.getFirstName() + " " + customer.getLastName()));
            doc.add(new Paragraph("Email: " + customer.getEmail()));
            doc.add(new Paragraph("Phone: " + customer.getPhoneNumber()));
            doc.add(new Paragraph("Address: " + customer.getBillingAddress()));
            doc.add(new Paragraph("Driving License: " + customer.getDrivingLicenseNumber() +
                    " (Expiry: " + customer.getDrivingLicenseExpiryDate() + ")"));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Car Details: " + car.getCarType().getBrand() + " " + car.getCarType().getModel()));
            doc.add(new Paragraph("Category: " + car.getCarType().getCategory()));
            doc.add(new Paragraph("Engine: " + car.getCarType().getEngine() + ", Power: " + car.getCarType().getPower() + " HP"));
            doc.add(new Paragraph("Max Speed: " + car.getCarType().getMaxSpeed() + " km/h"));
            doc.add(new Paragraph("Seats: " + car.getCarType().getSeats()));
            doc.add(new Paragraph("Transmission: " + car.getCarType().getTransmission()));
            doc.add(new Paragraph("Drive Type: " + car.getCarType().getDriveType()));
            doc.add(new Paragraph("Color: " + car.getColor()));
            doc.add(new Paragraph("License Plate: " + car.getLicensePlate()));
            doc.add(new Paragraph("VIN: " + car.getVin()));
            doc.add(new Paragraph("Insurance Expiry: " + car.getInsuranceExpiryDate()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Booking Details:"));
            doc.add(new Paragraph("Start Date: " + booking.getStartDate()));
            doc.add(new Paragraph("End Date: " + booking.getEndDate()));
            doc.add(new Paragraph("Deposit: " + booking.getDepositAmount()));
            doc.add(new Paragraph("Total Cost: " + booking.getTotalCost()));
            doc.add(new Paragraph("Payment Status: " + booking.getPaymentStatus()));
            doc.close();
            pdfBytes = baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            pdfBytes = new byte[0];
        }

        final byte[] finalPdfBytes = pdfBytes;
        new Thread(() -> EmailSender.sendEmailWithAttachment(
                customer.getEmail(),
                "Booking Created - Luxury Car Rental",
                htmlBody,
                finalPdfBytes
        )).start();

        return booking;
    }

    public Booking updateBooking(UUID id, Booking update) {
        Booking existing = state.getBookings().get(id);
        if (existing == null) throw new WebApplicationException("Booking not found", 404);

        // --- Update car if changed ---
        if (update.getCar() != null && !update.getCar().getId().equals(existing.getCar().getId())) {
            Car newCar = state.getCars().get(update.getCar().getId());
            if (newCar == null) throw new WebApplicationException("New car does not exist", 400);
            if (newCar.getStatus() != CarStatus.AVAILABLE) throw new WebApplicationException("New car is not available", 400);

            // Release old car and occupy new car
            existing.getCar().setStatus(CarStatus.AVAILABLE);
            newCar.setStatus(CarStatus.UNAVAILABLE);
            existing.setCar(newCar);
        }

        // --- Update customer if changed ---
        if (update.getCustomer() != null && !update.getCustomer().getId().equals(existing.getCustomer().getId())) {
            Customer newCustomer = state.getCustomers().get(update.getCustomer().getId());
            if (newCustomer == null) throw new WebApplicationException("New customer does not exist", 400);
            // Refund deposit from old customer
            existing.getCustomer().setBalance(existing.getCustomer().getBalance() + existing.getDepositAmount());
            // Deduct deposit from new customer
            if (newCustomer.getBalance() < existing.getDepositAmount())
                throw new WebApplicationException("New customer has insufficient balance", 400);
            newCustomer.setBalance(newCustomer.getBalance() - existing.getDepositAmount());
            existing.setCustomer(newCustomer);
        }

        // --- Update other fields ---
        if (update.getTotalCost() > 0) existing.setTotalCost(update.getTotalCost());
        if (update.getDepositAmount() > 0) existing.setDepositAmount(update.getDepositAmount());
        if (update.getBookingStatus() != null) existing.setBookingStatus(update.getBookingStatus());
        if (update.getPaymentStatus() != null) existing.setPaymentStatus(update.getPaymentStatus());
        if (update.getStartDate() != null) existing.setStartDate(update.getStartDate());
        if (update.getEndDate() != null) existing.setEndDate(update.getEndDate());

        return existing;
    }

    public boolean removeBooking(UUID id) {
        Booking booking = state.getBookings().remove(id);
        if (booking != null) {
            // 恢复车辆状态
            booking.getCar().setStatus(CarStatus.AVAILABLE);
            // 退还押金
            booking.getCustomer().setBalance(booking.getCustomer().getBalance() + booking.getDepositAmount());
            return true;
        }
        return false;
    }

    // ---------------- Booking Operations ----------------

    public Booking payForBooking(UUID bookingId) {
        Booking booking = state.getBookings().get(bookingId);
        if (booking == null)
            throw new WebApplicationException("Booking not found", 404);

        if (booking.getPaymentStatus() != PaymentStatus.PENDING)
            throw new WebApplicationException("Booking is not pending payment", 400);

        Customer customer = booking.getCustomer();
        double amount = booking.getDepositAmount() + booking.getTotalCost();

        // Simulated user account balance check
        if (customer.getBalance() < amount) {
            booking.setPaymentStatus(PaymentStatus.FAILED);
            throw new WebApplicationException("Insufficient balance", 400);
        }

        // Simulated deduction
        customer.setBalance(customer.getBalance() - amount);
        booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        booking.setBookingStatus(BookingStatus.CONFIRMED);

        // Send a payment success email
        new Thread(() -> EmailSender.sendEmail(
                customer.getEmail(),
                "Booking Confirmed - Luxury Car Rental",
                "Dear " + customer.getFirstName() + " " + customer.getLastName() + ",\n\n" +
                        "Your booking (ID: " + booking.getBookingId() + ") has been successfully confirmed.\n" +
                        "From: " + booking.getStartDate() + " To: " + booking.getEndDate() + "\n" +
                        "Deposit: " + booking.getDepositAmount() + "\n" +
                        "Total Cost: " + booking.getTotalCost() + "\n\n" +
                        "Thank you for choosing Luxury Car Rental!"
        )).start();

        return booking;
    }

    public void completeBooking(UUID bookingId) {
        Booking booking = state.getBookings().get(bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);

        Car car = booking.getCar();
        Customer customer = booking.getCustomer();

        car.setStatus(CarStatus.AVAILABLE);

        double remaining = booking.getTotalCost() - booking.getDepositAmount();
        if (remaining > 0) {
            if (customer.getBalance() < remaining) throw new WebApplicationException("Insufficient balance to complete booking", 400);
            customer.setBalance(customer.getBalance() - remaining);
        }

        booking.setBookingStatus(BookingStatus.COMPLETED);

        // Send a booking completion email notification
        String emailBody = String.format(
                "Booking Completed\n\n" +
                        "Dear %s %s,\n\n" +
                        "We are pleased to inform you that your booking %s has been completed successfully.\n\n" +
                        "We hope you enjoyed your experience. If you have any feedback, please feel free to contact us.\n\n" +
                        "Thank you for choosing Luxury Car Rental!\n" +
                        "Luxury Car Rental Team",
                booking.getCustomer().getFirstName(),
                booking.getCustomer().getLastName(),
                booking.getBookingId()
        );

        // Send email asynchronously
        new Thread(() -> EmailSender.sendEmail(
                booking.getCustomer().getEmail(),
                "Booking Completed - Luxury Car Rental",
                emailBody
        )).start();
    }

    public Booking rejectBooking(UUID bookingId, String reason) {
        Booking booking = state.getBookings().get(bookingId);
        if (booking == null)
            throw new WebApplicationException("Booking not found", 404);

        // Check the current status
        if (booking.getBookingStatus() == BookingStatus.CANCELLED ||
                booking.getBookingStatus() == BookingStatus.REJECTED)
            throw new WebApplicationException("Booking already cancelled or rejected", 400);

        // Update status
        booking.setBookingStatus(BookingStatus.REJECTED);

        // If it has been paid, it will be automatically refunded.
        if (booking.getPaymentStatus() == PaymentStatus.SUCCESSFUL) {
            Customer customer = booking.getCustomer();
            double refund = booking.getDepositAmount() + booking.getTotalCost();
            customer.setBalance(customer.getBalance() + refund);
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        // Send an email notification
        String emailBody = String.format(
                "Booking Rejected\n\n" +
                        "Dear %s %s,\n\n" +
                        "We regret to inform you that your booking %s has been rejected.\n\n" +
                        "%s" +
                        "If you have any questions or would like to make a new reservation, please contact our support team.\n\n" +
                        "Thank you for your understanding,\n" +
                        "Luxury Car Rental Team",
                booking.getCustomer().getFirstName(),
                booking.getCustomer().getLastName(),
                booking.getBookingId(),
                (reason != null && !reason.isBlank())
                        ? "Reason: " + reason + "\n\n"
                        : ""
        );

        // Send emails asynchronously
        new Thread(() -> EmailSender.sendEmail(
                booking.getCustomer().getEmail(),
                "Booking Rejected - Luxury Car Rental",
                emailBody
        )).start();

        return booking;
    }

    public void cancelBooking(UUID bookingId) {
        Booking booking = state.getBookings().get(bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);

        Car car = booking.getCar();
        Customer customer = booking.getCustomer();

        car.setStatus(CarStatus.AVAILABLE);
        customer.setBalance(customer.getBalance() + booking.getDepositAmount());

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setPaymentStatus(PaymentStatus.REFUNDED);

        // Send a cancellation email notification
        String emailBody = String.format(
                "Booking Cancelled\n\n" +
                        "Dear %s %s,\n\n" +
                        "Your booking %s has been cancelled successfully.\n\n" +
                        "If you have any questions or wish to make a new reservation, please contact our support team.\n\n" +
                        "Thank you,\n" +
                        "Luxury Car Rental Team",
                booking.getCustomer().getFirstName(),
                booking.getCustomer().getLastName(),
                booking.getBookingId()
        );

        // Send email asynchronously
        new Thread(() -> EmailSender.sendEmail(
                booking.getCustomer().getEmail(),
                "Booking Cancelled - Luxury Car Rental",
                emailBody
        )).start();
    }

    public void confirmBooking(UUID bookingId) {
        Booking booking = state.getBookings().get(bookingId);
        if (booking == null) {
            throw new WebApplicationException("Booking not found", 404);
        }

        // Check the current status
        if (booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new WebApplicationException("Only pending bookings can be confirmed", 400);
        }

        Car car = booking.getCar();
        Customer customer = booking.getCustomer();

        // Check whether the vehicle is available
        if (car.getStatus() != CarStatus.AVAILABLE) {
            throw new WebApplicationException("Car is not available", 400);
        }

        // Deduct deposit (error if balance is insufficient)
        double deposit = booking.getDepositAmount();
        if (customer.getBalance() < deposit) {
            throw new WebApplicationException("Insufficient balance for deposit", 400);
        }
        customer.setBalance(customer.getBalance() - deposit);

        // Update status
        car.setStatus(CarStatus.UNAVAILABLE);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);

        String subject = "Booking Confirmed  - Luxury Car Rental";
        String body = String.format(
                "Dear %s,\n\nYour booking for %s from %s to %s has been confirmed.\nTotal deposit: %.2f\n\nThank you for choosing us!",
                customer.getFirstName(),
                car.getCarType().getBrand() + " " + car.getCarType().getModel(),
                booking.getStartDate(),
                booking.getEndDate(),
                deposit
        );
        EmailSender.sendEmail(customer.getEmail(), subject, body);
    }

}