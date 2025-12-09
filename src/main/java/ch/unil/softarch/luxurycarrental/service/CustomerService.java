package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.Customer;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.service.EmailSender; // Assuming utility exists
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service class for managing Customer accounts.
 * <p>
 * Handles registration, authentication, profile updates, and password resets using JPA.
 * </p>
 */
@ApplicationScoped
public class CustomerService {

    @PersistenceContext(unitName = "LuxuryCarRentalPU")
    private EntityManager em;

    // Temporary storage for reset codes (Consider moving to DB in production)
    private final Map<String, VerificationCode> passwordResetCodes = new ConcurrentHashMap<>();

    private static class VerificationCode {
        String code;
        LocalDateTime createdAt;
        VerificationCode(String code, LocalDateTime createdAt) {
            this.code = code;
            this.createdAt = createdAt;
        }
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Registers a new customer.
     * Checks for duplicate email, phone, and license number.
     */
    @Transactional
    public Customer addCustomer(Customer customer) {
        // Efficient Duplicate Checks using JP-QL
        Long emailCount = em.createQuery("SELECT COUNT(c) FROM Customer c WHERE c.email = :email", Long.class)
                .setParameter("email", customer.getEmail())
                .getSingleResult();
        if (emailCount > 0) throw new WebApplicationException("Email already exists", 400);

        Long licenseCount = em.createQuery("SELECT COUNT(c) FROM Customer c WHERE c.drivingLicenseNumber = :license", Long.class)
                .setParameter("license", customer.getDrivingLicenseNumber())
                .getSingleResult();
        if (licenseCount > 0) throw new WebApplicationException("Driving license number already exists", 400);

        Long phoneCount = em.createQuery("SELECT COUNT(c) FROM Customer c WHERE c.phoneNumber = :phone", Long.class)
                .setParameter("phone", customer.getPhoneNumber())
                .getSingleResult();
        if (phoneCount > 0) throw new WebApplicationException("Phone number already exists", 400);

        // ID and CreationDate handled by entity @PrePersist
        em.persist(customer);

        // Send confirmation email asynchronously
        sendWelcomeEmail(customer);

        return customer;
    }

    private void sendWelcomeEmail(Customer customer) {
        new Thread(() -> {
            try {
                String to = customer.getEmail();
                String subject = "Welcome to Luxury Car Rental!";
                String body = "Hi " + customer.getFirstName() + ",\n\n" +
                        "Your account has been successfully created.\n\n" +
                        "Best regards,\nLuxury Car Rental Team";
                EmailSender.sendEmailAsync(to, subject, body);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public Customer getCustomer(UUID id) {
        Customer customer = em.find(Customer.class, id);
        if (customer == null) throw new WebApplicationException("Customer not found", 404);
        return customer;
    }

    public List<Customer> getAllCustomers() {
        return em.createQuery("SELECT c FROM Customer c", Customer.class).getResultList();
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    public Customer updateCustomer(UUID id, Customer update) {
        Customer existing = em.find(Customer.class, id);
        if (existing == null) throw new WebApplicationException("Customer not found", 404);

        if (update.getFirstName() != null) existing.setFirstName(update.getFirstName());
        if (update.getLastName() != null) existing.setLastName(update.getLastName());
        if (update.getEmail() != null) existing.setEmail(update.getEmail());
        if (update.getPhoneNumber() != null) existing.setPhoneNumber(update.getPhoneNumber());
        if (update.getDrivingLicenseNumber() != null) existing.setDrivingLicenseNumber(update.getDrivingLicenseNumber());
        if (update.getDrivingLicenseExpiryDate() != null) existing.setDrivingLicenseExpiryDate(update.getDrivingLicenseExpiryDate());
        if (update.getAge() > 0) existing.setAge(update.getAge());

        // Boolean fields need careful handling (isVerifiedIdentity)
        // Assuming we always trust the update object here, or check if it was set explicitly
        existing.setVerifiedIdentity(update.isVerifiedIdentity());

        if (update.getBillingAddress() != null) existing.setBillingAddress(update.getBillingAddress());
        if (update.getBalance() != 0.0) existing.setBalance(update.getBalance());

        return existing;
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @Transactional
    public boolean removeCustomer(UUID id) {
        Customer customer = em.find(Customer.class, id);
        if (customer == null) {
            throw new WebApplicationException("Customer not found", 404);
        }

        // Check for active bookings using JP-QL
        // We shouldn't delete a customer if they have PENDING or CONFIRMED bookings.
        Long activeBookings = em.createQuery(
                        "SELECT COUNT(b) FROM Booking b WHERE b.customer.id = :custId AND b.bookingStatus IN :statuses", Long.class)
                .setParameter("custId", id)
                .setParameter("statuses", List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED))
                .getSingleResult();

        if (activeBookings > 0) {
            throw new WebApplicationException("Cannot delete customer with active bookings", 400);
        }

        em.remove(customer);
        return true;
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    public Customer authenticate(String email, String password) {
        try {
            return em.createQuery("SELECT c FROM Customer c WHERE c.email = :email AND c.password = :password", Customer.class)
                    .setParameter("email", email)
                    .setParameter("password", password)
                    .getSingleResult();
        } catch (Exception e) {
            throw new WebApplicationException("Invalid email or password", 401);
        }
    }

    // -------------------------------------------------------------------------
    // Verification & Password Reset
    // -------------------------------------------------------------------------

    @Transactional
    public boolean verifyCustomer(UUID id) {
        Customer customer = em.find(Customer.class, id);
        if (customer == null) return false;

        customer.setVerifiedIdentity(true);
        return true;
    }

    public void sendPasswordResetCode(String email) {
        // Check if customer exists
        Long count = em.createQuery("SELECT COUNT(c) FROM Customer c WHERE c.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();

        if (count == 0) throw new WebApplicationException("Customer not found", 404);

        // Generate Code
        String code = String.format("%06d", new Random().nextInt(999999));
        passwordResetCodes.put(email, new VerificationCode(code, LocalDateTime.now()));

        // Send Email
        String subject = "Password Reset Code";
        String body = "Your code is: " + code;
        EmailSender.sendEmailAsync(email, subject, body);
    }

    @Transactional
    public void resetPasswordWithCode(String email, String code, String newPassword) {
        // Get customer entity (Managed)
        Customer customer;
        try {
            customer = em.createQuery("SELECT c FROM Customer c WHERE c.email = :email", Customer.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (Exception e) {
            throw new WebApplicationException("Customer not found", 404);
        }

        // Verify Code
        VerificationCode stored = passwordResetCodes.get(email);
        if (stored == null) throw new WebApplicationException("No verification code found", 400);

        if (Duration.between(stored.createdAt, LocalDateTime.now()).toMinutes() >= 5) {
            passwordResetCodes.remove(email);
            throw new WebApplicationException("Verification code expired", 400);
        }

        if (!stored.code.equals(code)) throw new WebApplicationException("Invalid verification code", 400);

        // Update Password
        customer.setPassword(newPassword);
        passwordResetCodes.remove(email);

        EmailSender.sendEmailAsync(email, "Password Changed", "Your password has been changed successfully.");
    }
}