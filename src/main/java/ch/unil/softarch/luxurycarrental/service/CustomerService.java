package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.Booking;
import ch.unil.softarch.luxurycarrental.domain.entities.Customer;
import ch.unil.softarch.luxurycarrental.domain.enums.BookingStatus;
import ch.unil.softarch.luxurycarrental.repository.BookingRepository;
import ch.unil.softarch.luxurycarrental.repository.CustomerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class CustomerService {

    @Inject
    private CustomerRepository customerRepo; // Inject the repository for DB operations
    @Inject
    private BookingService bookingService;

    // Temporary in-memory storage for password reset codes
    private final Map<String, VerificationCode> passwordResetCodes = new HashMap<>();

    // ---------- CREATE ----------
    /**
     * Add a new customer.
     * Checks for unique email, phone, and driving license before saving.
     */
    public Customer addCustomer(Customer customer) {
        if (customerRepo.findByEmail(customer.getEmail()).isPresent()) {
            throw new WebApplicationException("Email already exists", 400);
        }
        if (customerRepo.findByPhoneNumber(customer.getPhoneNumber()).isPresent()) {
            throw new WebApplicationException("Phone number already exists", 400);
        }
        if (customerRepo.findByDrivingLicenseNumber(customer.getDrivingLicenseNumber()).isPresent()) {
            throw new WebApplicationException("Driving license already exists", 400);
        }

        if (customer.getId() == null) customer.setId(UUID.randomUUID());
        if (customer.getCreationDate() == null) customer.setCreationDate(LocalDateTime.now());

        Customer saved = customerRepo.save(customer);

        // Send welcome email asynchronously
        String subject = "Welcome to Luxury Car Rental!";
        String body = "Hi " + saved.getFirstName() + ",\n\n" +
                "Your account has been successfully created.\n" +
                "Email: " + saved.getEmail() + "\n" +
                "Phone: " + saved.getPhoneNumber() + "\n" +
                "Driving License: " + saved.getDrivingLicenseNumber() + "\n\n" +
                "Best regards,\nLuxury Car Rental Team";
        EmailSender.sendEmailAsync(saved.getEmail(), subject, body);

        return saved;
    }

    // ---------- READ ----------
    /**
     * Get customer by ID.
     */
    public Customer getCustomer(UUID id) {
        return customerRepo.findById(id)
                .orElseThrow(() -> new WebApplicationException("Customer not found", 404));
    }

    /**
     * Get all customers.
     */
    public List<Customer> getAllCustomers() {
        return customerRepo.findAll();
    }

    // ---------- UPDATE ----------
    /**
     * Update customer details.
     * Only non-null or non-zero fields will be updated.
     */
    public Customer updateCustomer(UUID id, Customer update) {
        Customer existing = getCustomer(id);

        if (update.getFirstName() != null) existing.setFirstName(update.getFirstName());
        if (update.getLastName() != null) existing.setLastName(update.getLastName());
        if (update.getEmail() != null) existing.setEmail(update.getEmail());
        if (update.getPhoneNumber() != null) existing.setPhoneNumber(update.getPhoneNumber());
        if (update.getDrivingLicenseNumber() != null)
            existing.setDrivingLicenseNumber(update.getDrivingLicenseNumber());
        if (update.getDrivingLicenseExpiryDate() != null)
            existing.setDrivingLicenseExpiryDate(update.getDrivingLicenseExpiryDate());
        if (update.getAge() > 0) existing.setAge(update.getAge());
        existing.setVerifiedIdentity(update.isVerifiedIdentity());
        if (update.getBillingAddress() != null) existing.setBillingAddress(update.getBillingAddress());
        if (update.getBalance() != 0.0) existing.setBalance(update.getBalance());

        return customerRepo.save(existing);
    }

    // ---------- DELETE ----------
    /**
     * Delete customer by ID.
     * Prevent deletion if customer has active bookings.
     */
    public boolean removeCustomer(UUID customerId) {
        Customer customer = getCustomer(customerId);

        // Use BookingService to check if customer has any active bookings
        List<Booking> customerBookings = bookingService.getBookingsByCustomerId(customerId);
        boolean hasActiveBookings = !customerBookings.isEmpty();

        if (hasActiveBookings) {
            throw new WebApplicationException("Cannot delete customer with active bookings", 400);
        }

        return customerRepo.delete(customerId);
    }

    // ---------- AUTHENTICATION ----------
    /**
     * Authenticate customer by email and password.
     */
    public Customer authenticate(String email, String password) {
        Customer customer = customerRepo.findByEmail(email)
                .orElseThrow(() -> new WebApplicationException("Invalid email or password", 401));

        if (!customer.getPassword().equals(password)) {
            throw new WebApplicationException("Invalid email or password", 401);
        }
        return customer;
    }

    // ---------- PASSWORD RESET ----------
    /**
     * Send password reset code to customer's email.
     */
    public void sendPasswordResetCode(String email) {
        Customer customer = customerRepo.findByEmail(email)
                .orElseThrow(() -> new WebApplicationException("Customer not found", 404));

        String code = String.format("%06d", new Random().nextInt(999999));
        passwordResetCodes.put(email, new VerificationCode(code, LocalDateTime.now()));

        String subject = "Password Reset Code";
        String body = "Hello " + customer.getFirstName() + ",\n\n" +
                "Your password reset code is: " + code + "\n" +
                "This code will expire in 5 minutes.\n\n" +
                "Luxury Car Rental Team";
        EmailSender.sendEmailAsync(email, subject, body);
    }

    /**
     * Reset password using verification code.
     */
    public void resetPasswordWithCode(String email, String code, String newPassword) {
        Customer customer = customerRepo.findByEmail(email)
                .orElseThrow(() -> new WebApplicationException("Customer not found", 404));

        VerificationCode stored = passwordResetCodes.get(email);
        if (stored == null || Duration.between(stored.getCreatedAt(), LocalDateTime.now()).toMinutes() >= 5) {
            passwordResetCodes.remove(email);
            throw new WebApplicationException("Verification code expired or not found", 400);
        }

        if (!stored.getCode().equals(code)) {
            throw new WebApplicationException("Invalid verification code", 400);
        }

        customer.setPassword(newPassword);
        customerRepo.save(customer);
        passwordResetCodes.remove(email);

        EmailSender.sendEmailAsync(email, "Password Changed",
                "Hello " + customer.getFirstName() + ",\n\nYour password has been successfully updated.");
    }

    // ---------- IDENTITY VERIFICATION ----------
    /**
     * Verify customer's identity by ID.
     */
    public boolean verifyCustomer(UUID id) {
        Customer customer = getCustomer(id);
        customer.setVerifiedIdentity(true);
        customerRepo.save(customer);
        return true;
    }

    // Inner class for temporary verification codes
    private static class VerificationCode {
        private final String code;
        private final LocalDateTime createdAt;

        public VerificationCode(String code, LocalDateTime createdAt) {
            this.code = code;
            this.createdAt = createdAt;
        }

        public String getCode() { return code; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}