package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.*;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.CarStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.PaymentStatus;
import ch.unil.softarch.luxurycarrental.repository.BookingRepository;
import ch.unil.softarch.luxurycarrental.repository.CarRepository;
import ch.unil.softarch.luxurycarrental.repository.CustomerRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.io.ByteArrayOutputStream;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;

@ApplicationScoped
public class BookingService {

    @Inject
    private BookingRepository bookingRepo;

    @Inject
    private CustomerRepository customerRepo;

    @Inject
    private CarRepository carRepo;

    // ---------------- CRUD ----------------
    public Collection<Booking> getAllBookings() {
        return bookingRepo.findAll();
    }

    public Booking getBooking(UUID id) {
        return bookingRepo.findById(id)
                .orElseThrow(() -> new WebApplicationException("Booking not found", 404));
    }

    public List<Booking> getBookingsByCustomerId(UUID customerId) {
        return bookingRepo.findAll().stream()
                .filter(b -> b.getCustomer().getId().equals(customerId))
                .collect(Collectors.toList());
    }

    public List<Booking> getBookingsByCarId(UUID carId) {
        return bookingRepo.findAll().stream()
                .filter(b -> b.getCar().getId().equals(carId))
                .collect(Collectors.toList());
    }

    // ---------------- CREATE ----------------
    public Booking createBooking(UUID customerId, UUID carId, LocalDate startDate, LocalDate endDate) {

        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new WebApplicationException("Customer not found", 400));
        Car car = carRepo.findById(carId)
                .orElseThrow(() -> new WebApplicationException("Car not found", 400));

        if (car.getStatus() != CarStatus.AVAILABLE)
            throw new WebApplicationException("Car not available", 400);

        if (startDate == null || endDate == null || endDate.isBefore(startDate))
            throw new WebApplicationException("Invalid booking dates", 400);

        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        double totalCost = car.getDailyRentalPrice() * days;
        double deposit = car.getDepositAmount();

        Booking booking = new Booking();
        booking.setBookingId(UUID.randomUUID());
        booking.setCustomer(customer);
        booking.setCar(car);
        booking.setStartDate(startDate);
        booking.setEndDate(endDate);
        booking.setDepositAmount(deposit);
        booking.setTotalCost(totalCost);
        booking.setBookingStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.PENDING);

        bookingRepo.save(booking);

        sendBookingConfirmationEmail(customer, booking);

        return booking;
    }

    // ---------------- UPDATE ----------------
    public Booking updateBooking(UUID bookingId, Booking update) {
        Booking existing = getBooking(bookingId);

        if (update.getCar() != null && !update.getCar().getId().equals(existing.getCar().getId())) {
            Car newCar = carRepo.findById(update.getCar().getId())
                    .orElseThrow(() -> new WebApplicationException("New car not found", 400));
            if (newCar.getStatus() != CarStatus.AVAILABLE)
                throw new WebApplicationException("New car not available", 400);

            existing.getCar().setStatus(CarStatus.AVAILABLE);
            newCar.setStatus(CarStatus.UNAVAILABLE);
            existing.setCar(newCar);
        }

        if (update.getCustomer() != null && !update.getCustomer().getId().equals(existing.getCustomer().getId())) {
            Customer newCustomer = customerRepo.findById(update.getCustomer().getId())
                    .orElseThrow(() -> new WebApplicationException("New customer not found", 400));
            if (newCustomer.getBalance() < existing.getDepositAmount())
                throw new WebApplicationException("Insufficient balance for new customer", 400);

            existing.getCustomer().setBalance(existing.getCustomer().getBalance() + existing.getDepositAmount());
            newCustomer.setBalance(newCustomer.getBalance() - existing.getDepositAmount());
            existing.setCustomer(newCustomer);
        }

        if (update.getBookingStatus() != null) existing.setBookingStatus(update.getBookingStatus());
        if (update.getPaymentStatus() != null) existing.setPaymentStatus(update.getPaymentStatus());
        if (update.getDepositAmount() > 0) existing.setDepositAmount(update.getDepositAmount());
        if (update.getTotalCost() > 0) existing.setTotalCost(update.getTotalCost());
        if (update.getStartDate() != null) existing.setStartDate(update.getStartDate());
        if (update.getEndDate() != null) existing.setEndDate(update.getEndDate());

        bookingRepo.save(existing);
        return existing;
    }

    // ---------------- DELETE ----------------
    public boolean removeBooking(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        booking.getCar().setStatus(CarStatus.AVAILABLE);
        booking.getCustomer().setBalance(booking.getCustomer().getBalance() + booking.getDepositAmount());
        return bookingRepo.delete(bookingId);
    }

    // ---------------- BOOKING OPERATIONS ----------------
    public Booking payForBooking(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        if (booking.getPaymentStatus() != PaymentStatus.PENDING)
            throw new WebApplicationException("Booking not pending payment", 400);

        Customer customer = booking.getCustomer();
        double amount = booking.getDepositAmount() + booking.getTotalCost();
        if (customer.getBalance() < amount) {
            booking.setPaymentStatus(PaymentStatus.FAILED);
            throw new WebApplicationException("Insufficient balance", 400);
        }

        customer.setBalance(customer.getBalance() - amount);
        booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        booking.setBookingStatus(BookingStatus.CONFIRMED);

        bookingRepo.save(booking);

        sendPaymentSuccessEmail(customer, booking);
        return booking;
    }

    public void confirmBooking(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        if (booking.getBookingStatus() != BookingStatus.PENDING)
            throw new WebApplicationException("Only pending bookings can be confirmed", 400);

        Car car = booking.getCar();
        Customer customer = booking.getCustomer();

        if (car.getStatus() != CarStatus.AVAILABLE)
            throw new WebApplicationException("Car not available", 400);

        double deposit = booking.getDepositAmount();
        if (customer.getBalance() < deposit)
            throw new WebApplicationException("Insufficient balance for deposit", 400);

        customer.setBalance(customer.getBalance() - deposit);
        car.setStatus(CarStatus.UNAVAILABLE);

        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        bookingRepo.save(booking);

        sendBookingConfirmedEmail(customer, booking);
    }

    public void cancelBooking(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        booking.getCar().setStatus(CarStatus.AVAILABLE);
        booking.getCustomer().setBalance(booking.getCustomer().getBalance() + booking.getDepositAmount());
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setPaymentStatus(PaymentStatus.REFUNDED);

        bookingRepo.save(booking);
        sendBookingCancelledEmail(booking);
    }

    public void completeBooking(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        booking.getCar().setStatus(CarStatus.AVAILABLE);

        double remaining = booking.getTotalCost() - booking.getDepositAmount();
        Customer customer = booking.getCustomer();
        if (remaining > 0) {
            if (customer.getBalance() < remaining)
                throw new WebApplicationException("Insufficient balance to complete booking", 400);
            customer.setBalance(customer.getBalance() - remaining);
        }

        booking.setBookingStatus(BookingStatus.COMPLETED);
        bookingRepo.save(booking);

        sendBookingCompletedEmail(booking);
    }

    public Booking rejectBooking(UUID bookingId, String reason) {
        Booking booking = getBooking(bookingId);
        if (booking.getBookingStatus() == BookingStatus.CANCELLED || booking.getBookingStatus() == BookingStatus.REJECTED)
            throw new WebApplicationException("Booking already cancelled or rejected", 400);

        booking.setBookingStatus(BookingStatus.REJECTED);
        if (booking.getPaymentStatus() == PaymentStatus.SUCCESSFUL) {
            Customer customer = booking.getCustomer();
            double refund = booking.getDepositAmount() + booking.getTotalCost();
            customer.setBalance(customer.getBalance() + refund);
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        bookingRepo.save(booking);
        sendBookingRejectedEmail(booking, reason);
        return booking;
    }

    // ---------------- EMAIL / PDF ----------------
    private void sendBookingConfirmationEmail(Customer customer, Booking booking) {
        new Thread(() -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                PdfWriter writer = new PdfWriter(baos);
                PdfDocument pdfDoc = new PdfDocument(writer);
                Document doc = new Document(pdfDoc);
                doc.add(new Paragraph("Booking Confirmation"));
                doc.add(new Paragraph("Customer: " + customer.getFirstName() + " " + customer.getLastName()));
                doc.add(new Paragraph("Booking ID: " + booking.getBookingId()));
                doc.add(new Paragraph("Car: " + booking.getCar().getCarType().getBrand() + " " + booking.getCar().getCarType().getModel()));
                doc.add(new Paragraph("From: " + booking.getStartDate() + " To: " + booking.getEndDate()));
                doc.add(new Paragraph("Total: " + booking.getTotalCost()));
                doc.add(new Paragraph("Deposit: " + booking.getDepositAmount()));
                doc.close();

                EmailSender.sendEmailWithAttachment(
                        customer.getEmail(),
                        "Booking Created - Luxury Car Rental",
                        "Your booking has been created.",
                        baos.toByteArray()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendPaymentSuccessEmail(Customer customer, Booking booking) {
        new Thread(() -> EmailSender.sendEmail(
                customer.getEmail(),
                "Booking Confirmed - Luxury Car Rental",
                "Your booking " + booking.getBookingId() + " has been successfully paid."
        )).start();
    }

    private void sendBookingConfirmedEmail(Customer customer, Booking booking) {
        new Thread(() -> EmailSender.sendEmail(
                customer.getEmail(),
                "Booking Confirmed - Luxury Car Rental",
                "Your booking " + booking.getBookingId() + " has been confirmed."
        )).start();
    }

    private void sendBookingCancelledEmail(Booking booking) {
        new Thread(() -> EmailSender.sendEmail(
                booking.getCustomer().getEmail(),
                "Booking Cancelled - Luxury Car Rental",
                "Your booking " + booking.getBookingId() + " has been cancelled."
        )).start();
    }

    private void sendBookingCompletedEmail(Booking booking) {
        new Thread(() -> EmailSender.sendEmail(
                booking.getCustomer().getEmail(),
                "Booking Completed - Luxury Car Rental",
                "Your booking " + booking.getBookingId() + " has been completed."
        )).start();
    }

    private void sendBookingRejectedEmail(Booking booking, String reason) {
        new Thread(() -> EmailSender.sendEmail(
                booking.getCustomer().getEmail(),
                "Booking Rejected - Luxury Car Rental",
                "Your booking " + booking.getBookingId() + " has been rejected. Reason: " + (reason != null ? reason : "N/A")
        )).start();
    }
}