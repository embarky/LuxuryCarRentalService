package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.*;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.CarStatus;
import ch.unil.softarch.luxurycarrental.domain.enums.PaymentStatus;
import ch.unil.softarch.luxurycarrental.service.EmailSender; // Assuming utility
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service class for managing Bookings.
 * <p>
 * Handles the complete lifecycle of a rental booking, including creation,
 * payments, confirmations, completions, and cancellations.
 * Ensures data consistency across Car, Customer, and Booking entities.
 * </p>
 */
@ApplicationScoped
public class BookingService {

    @PersistenceContext(unitName = "LuxuryCarRentalPU")
    private EntityManager em;

    // -------------------------------------------------------------------------
    // CRUD Operations (Read)
    // -------------------------------------------------------------------------

    public Booking getBooking(UUID id) {
        Booking booking = em.find(Booking.class, id);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);
        return booking;
    }

    public List<Booking> getAllBookings() {
        return em.createQuery("SELECT b FROM Booking b", Booking.class).getResultList();
    }

    public List<Booking> getBookingsByCustomerId(UUID customerId) {
        return em.createQuery("SELECT b FROM Booking b WHERE b.customer.id = :cid", Booking.class)
                .setParameter("cid", customerId)
                .getResultList();
    }

    public List<Booking> getBookingsByCarId(UUID carId) {
        return em.createQuery("SELECT b FROM Booking b WHERE b.car.id = :cid", Booking.class)
                .setParameter("cid", carId)
                .getResultList();
    }

    // -------------------------------------------------------------------------
    // Core Business Logic (Create/Update/Delete)
    // -------------------------------------------------------------------------

    @Transactional
    public Booking createBooking(UUID customerId, UUID carId, LocalDate startDate, LocalDate endDate) {
        // 1. Retrieve Entities
        Customer customer = em.find(Customer.class, customerId);
        Car car = em.find(Car.class, carId);

        // 2. Validate
        if (customer == null) throw new WebApplicationException("Customer does not exist", 400);
        if (car == null) throw new WebApplicationException("Car does not exist", 400);
        if (car.getStatus() != CarStatus.AVAILABLE) throw new WebApplicationException("Car is not available", 400);
        if (startDate == null || endDate == null) throw new WebApplicationException("Dates required", 400);
        if (endDate.isBefore(startDate)) throw new WebApplicationException("Invalid date range", 400);

        // 3. Calculate Costs
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        double totalCost = car.getDailyRentalPrice() * days;
        double depositAmount = car.getDepositAmount();

        // 4. Create Entity (ID handled by @PrePersist)
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setCar(car);
        booking.setStartDate(startDate);
        booking.setEndDate(endDate);
        booking.setDepositAmount(depositAmount);
        booking.setTotalCost(totalCost);
        booking.setBookingStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.PENDING);

        // 5. Persist
        em.persist(booking);

        // 6. Generate PDF & Email (Asynchronous)
        // Note: Fetch required data now to avoid LazyInitializationException in the async thread
        byte[] pdfBytes = generateBookingPdf(booking, customer, car);
        sendBookingCreationEmail(customer, pdfBytes);

        return booking;
    }

    @Transactional
    public Booking updateBooking(UUID id, Booking update) {
        Booking existing = em.find(Booking.class, id);
        if (existing == null) throw new WebApplicationException("Booking not found", 404);

        // Handle Car Change
        if (update.getCar() != null && !update.getCar().getId().equals(existing.getCar().getId())) {
            Car newCar = em.find(Car.class, update.getCar().getId());
            if (newCar == null || newCar.getStatus() != CarStatus.AVAILABLE) {
                throw new WebApplicationException("New car unavailable", 400);
            }
            // Release old, Book new
            existing.getCar().setStatus(CarStatus.AVAILABLE);
            newCar.setStatus(CarStatus.UNAVAILABLE);
            existing.setCar(newCar);
        }

        // Handle Customer Change (and deposit transfer)
        if (update.getCustomer() != null && !update.getCustomer().getId().equals(existing.getCustomer().getId())) {
            Customer newCustomer = em.find(Customer.class, update.getCustomer().getId());
            if (newCustomer == null) throw new WebApplicationException("New customer not found", 400);

            // Refund old
            Customer oldCustomer = existing.getCustomer();
            oldCustomer.setBalance(oldCustomer.getBalance() + existing.getDepositAmount());

            // Charge new
            if (newCustomer.getBalance() < existing.getDepositAmount()) {
                throw new WebApplicationException("Insufficient balance", 400);
            }
            newCustomer.setBalance(newCustomer.getBalance() - existing.getDepositAmount());
            existing.setCustomer(newCustomer);
        }

        // Update basic fields
        if (update.getTotalCost() > 0) existing.setTotalCost(update.getTotalCost());
        if (update.getDepositAmount() > 0) existing.setDepositAmount(update.getDepositAmount());
        if (update.getBookingStatus() != null) existing.setBookingStatus(update.getBookingStatus());
        if (update.getPaymentStatus() != null) existing.setPaymentStatus(update.getPaymentStatus());
        if (update.getStartDate() != null) existing.setStartDate(update.getStartDate());
        if (update.getEndDate() != null) existing.setEndDate(update.getEndDate());

        return existing;
    }

    @Transactional
    public boolean removeBooking(UUID id) {
        Booking booking = em.find(Booking.class, id);
        if (booking != null) {
            // Restore car status
            booking.getCar().setStatus(CarStatus.AVAILABLE);
            // Refund deposit if applicable (logic depends on policy, assuming full refund here)
            Customer cust = booking.getCustomer();
            cust.setBalance(cust.getBalance() + booking.getDepositAmount());

            em.remove(booking);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Workflow Operations
    // -------------------------------------------------------------------------

    @Transactional
    public Booking payForBooking(UUID bookingId) {
        Booking booking = em.find(Booking.class, bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);

        if (booking.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new WebApplicationException("Booking not pending payment", 400);
        }

        Customer customer = booking.getCustomer();
        double amount = booking.getDepositAmount() + booking.getTotalCost();

        if (customer.getBalance() < amount) {
            booking.setPaymentStatus(PaymentStatus.FAILED);
            throw new WebApplicationException("Insufficient balance", 400);
        }

        customer.setBalance(customer.getBalance() - amount);
        booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);
        booking.setBookingStatus(BookingStatus.CONFIRMED);

        // Async Email
        sendSimpleEmail(customer.getEmail(), "Booking Confirmed",
                "Your booking " + booking.getBookingId() + " is confirmed.");

        return booking;
    }

    @Transactional
    public void confirmBooking(UUID bookingId) {
        Booking booking = em.find(Booking.class, bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);

        if (booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new WebApplicationException("Only pending bookings can be confirmed", 400);
        }

        Car car = booking.getCar();
        Customer customer = booking.getCustomer();

        if (car.getStatus() != CarStatus.AVAILABLE) {
            throw new WebApplicationException("Car is not available", 400);
        }

        double deposit = booking.getDepositAmount();
        if (customer.getBalance() < deposit) {
            throw new WebApplicationException("Insufficient balance", 400);
        }

        // Deduct and Update Status
        customer.setBalance(customer.getBalance() - deposit);
        car.setStatus(CarStatus.UNAVAILABLE);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.SUCCESSFUL);

        sendSimpleEmail(customer.getEmail(), "Booking Confirmed",
                "Booking confirmed. Deposit deducted: " + deposit);
    }

    @Transactional
    public void completeBooking(UUID bookingId) {
        Booking booking = em.find(Booking.class, bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);

        Car car = booking.getCar();
        Customer customer = booking.getCustomer();

        // Release Car
        car.setStatus(CarStatus.AVAILABLE);

        // Final Payment (Total - Deposit)
        double remaining = booking.getTotalCost() - booking.getDepositAmount();
        if (remaining > 0) {
            if (customer.getBalance() < remaining) {
                throw new WebApplicationException("Insufficient balance", 400);
            }
            customer.setBalance(customer.getBalance() - remaining);
        }

        booking.setBookingStatus(BookingStatus.COMPLETED);
        sendSimpleEmail(customer.getEmail(), "Booking Completed", "Thank you for riding with us.");
    }

    @Transactional
    public void cancelBooking(UUID bookingId) {
        Booking booking = em.find(Booking.class, bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);

        // Refund and Release
        booking.getCar().setStatus(CarStatus.AVAILABLE);
        booking.getCustomer().setBalance(booking.getCustomer().getBalance() + booking.getDepositAmount());

        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setPaymentStatus(PaymentStatus.REFUNDED);

        sendSimpleEmail(booking.getCustomer().getEmail(), "Booking Cancelled", "Your booking has been cancelled.");
    }

    @Transactional
    public Booking rejectBooking(UUID bookingId, String reason) {
        Booking booking = em.find(Booking.class, bookingId);
        if (booking == null) throw new WebApplicationException("Booking not found", 404);

        if (booking.getBookingStatus() == BookingStatus.CANCELLED || booking.getBookingStatus() == BookingStatus.REJECTED) {
            throw new WebApplicationException("Already cancelled/rejected", 400);
        }

        booking.setBookingStatus(BookingStatus.REJECTED);

        // Auto Refund if paid
        if (booking.getPaymentStatus() == PaymentStatus.SUCCESSFUL) {
            Customer c = booking.getCustomer();
            c.setBalance(c.getBalance() + booking.getDepositAmount() + booking.getTotalCost());
            booking.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        sendSimpleEmail(booking.getCustomer().getEmail(), "Booking Rejected", "Reason: " + reason);
        return booking;
    }

    // -------------------------------------------------------------------------
    // Helpers (PDF & Email)
    // -------------------------------------------------------------------------

    private byte[] generateBookingPdf(Booking booking, Customer customer, Car car) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document doc = new Document(pdfDoc);

            // Note: Accessing carType properties here is safe because 'car' is still attached
            // to the persistence context within the transaction scope of createBooking
            doc.add(new Paragraph("Booking Confirmation"));
            doc.add(new Paragraph("Customer: " + customer.getFirstName()));
            doc.add(new Paragraph("Car: " + car.getCarType().getBrand() + " " + car.getCarType().getModel()));
            doc.add(new Paragraph("Total Cost: " + booking.getTotalCost()));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return new byte[0];
        }
    }

    private void sendBookingCreationEmail(Customer customer, byte[] pdfBytes) {
        String htmlBody = "<h2>Booking Created</h2><p>Your booking is pending confirmation.</p>";
        new Thread(() -> EmailSender.sendEmailWithAttachment(
                customer.getEmail(), "Booking Created", htmlBody, pdfBytes
        )).start();
    }

    private void sendSimpleEmail(String to, String subject, String body) {
        new Thread(() -> EmailSender.sendEmail(to, subject, body)).start();
    }
}