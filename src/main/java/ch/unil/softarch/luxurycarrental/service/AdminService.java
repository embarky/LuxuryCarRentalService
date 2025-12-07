package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.Admin;
import ch.unil.softarch.luxurycarrental.repository.AdminRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class AdminService {

    @Inject
    private AdminRepository adminRepo; // Inject the Admin repository for DB operations

    // Temporary in-memory storage for password reset codes
    private final Map<String, VerificationCode> passwordResetCodes = new HashMap<>();

    // ---------- CREATE ----------
    /**
     * Add a new administrator.
     * Checks for unique username and email before saving to database.
     */
    public Admin addAdmin(Admin admin) {
        // Check if username already exists in the database
        if (adminRepo.findByUsername(admin.getUsername()).isPresent()) {
            throw new WebApplicationException("Username already exists", 400);
        }

        // Check if email already exists in the database
        if (adminRepo.findByEmail(admin.getEmail()).isPresent()) {
            throw new WebApplicationException("Email already exists", 400);
        }

        // Set ID and timestamps if not provided
        if (admin.getId() == null) admin.setId(UUID.randomUUID());
        if (admin.getCreatedAt() == null) admin.setCreatedAt(LocalDateTime.now());
        if (admin.getUpdatedAt() == null) admin.setUpdatedAt(LocalDateTime.now());

        // Save admin to database
        Admin saved = adminRepo.save(admin);

        // Send welcome email
        String subject = "Welcome to Luxury Car Rental Admin Panel";
        String body = "Hi " + saved.getName() + ",\nYour account has been created.\nUsername: " + saved.getUsername();
        EmailSender.sendEmailAsync(saved.getEmail(), subject, body);

        return saved;
    }

    // ---------- READ ----------
    /**
     * Get administrator by ID.
     * @param id Admin UUID
     * @return Admin object
     */
    public Admin getAdmin(UUID id) {
        return adminRepo.findById(id).orElseThrow(() -> new WebApplicationException("Admin not found", 404));
    }

    /**
     * Get all administrators.
     * @return List of Admin objects
     */
    public List<Admin> getAllAdmins() {
        return adminRepo.findAll();
    }

    // ---------- UPDATE ----------
    /**
     * Update administrator details.
     * Only non-null fields will be updated.
     */
    public Admin updateAdmin(UUID id, Admin update) {
        // Retrieve existing admin or throw 404 if not found
        Admin existing = getAdmin(id);

        // ---------- USERNAME UNIQUENESS CHECK ----------
        // Only check uniqueness if the username is being changed
        if (update.getUsername() != null && !update.getUsername().equals(existing.getUsername())) {
            adminRepo.findByUsername(update.getUsername()).ifPresent(a -> {
                // If another admin with this username exists, throw a 400 error
                if (!a.getId().equals(id)) {
                    throw new WebApplicationException("Username already exists", 400);
                }
            });
            existing.setUsername(update.getUsername());
        }

        // ---------- EMAIL UNIQUENESS CHECK ----------
        // Only check uniqueness if the email is being changed
        if (update.getEmail() != null && !update.getEmail().equals(existing.getEmail())) {
            adminRepo.findByEmail(update.getEmail()).ifPresent(a -> {
                // If another admin with this email exists, throw a 400 error
                if (!a.getId().equals(id)) {
                    throw new WebApplicationException("Email already exists", 400);
                }
            });
            existing.setEmail(update.getEmail());
        }

        // ---------- PATCH UPDATE FOR OTHER FIELDS ----------
        // Update name if provided
        if (update.getName() != null) {
            existing.setName(update.getName());
        }

        // Update password only if provided and not blank
        if (update.getPassword() != null && !update.getPassword().isBlank()) {
            // TODO: Add password encryption here
            existing.setPassword(update.getPassword());
        }

        // Always update the timestamp
        existing.setUpdatedAt(LocalDateTime.now());

        // Save updated admin to the database
        return adminRepo.save(existing);
    }

    // ---------- DELETE ----------
    /**
     * Remove administrator by ID.
     * @param id Admin UUID
     * @return true if removed successfully
     */
    public boolean removeAdmin(UUID id) {
        return adminRepo.delete(id);
    }

    // ---------- AUTHENTICATION ----------
    /**
     * Authenticate administrator by email and password.
     * @param email Admin email
     * @param password Admin password
     * @return Admin object if credentials are valid
     */
    public Admin authenticate(String email, String password) {
        Admin admin = adminRepo.findByEmail(email)
                .orElseThrow(() -> new WebApplicationException("Invalid email or password", 401));
        if (!admin.getPassword().equals(password)) {
            throw new WebApplicationException("Invalid email or password", 401);
        }
        return admin;
    }

    // ---------- PASSWORD RESET ----------
    /**
     * Send password reset code to admin's email.
     * @param email Admin email
     */
    public void sendAdminPasswordResetCodeByEmail(String email) {
        Admin admin = adminRepo.findByEmail(email)
                .orElseThrow(() -> new WebApplicationException("Admin not found", 404));

        // Generate 6-digit random code
        String code = String.format("%06d", new Random().nextInt(999999));
        passwordResetCodes.put(admin.getEmail(), new VerificationCode(code, LocalDateTime.now()));

        // Prepare email
        String subject = "Admin Password Reset Code";
        String body = "Your code is: " + code + " (expires in 5 minutes)";
        EmailSender.sendEmailAsync(admin.getEmail(), subject, body);
    }

    /**
     * Reset administrator password using verification code.
     * @param email Admin email
     * @param code Verification code
     * @param newPassword New password
     */
    public void resetAdminPasswordWithCodeByEmail(String email, String code, String newPassword) {
        Admin admin = adminRepo.findByEmail(email)
                .orElseThrow(() -> new WebApplicationException("Admin not found", 404));

        VerificationCode stored = passwordResetCodes.get(email);
        if (stored == null || Duration.between(stored.getCreatedAt(), LocalDateTime.now()).toMinutes() >= 5) {
            passwordResetCodes.remove(email);
            throw new WebApplicationException("Verification code expired or not found", 400);
        }

        if (!stored.getCode().equals(code)) {
            throw new WebApplicationException("Invalid verification code", 400);
        }

        // Update password in database
        admin.setPassword(newPassword);
        admin.setUpdatedAt(LocalDateTime.now());
        adminRepo.save(admin);

        // Remove used code
        passwordResetCodes.remove(email);

        // Send confirmation email
        EmailSender.sendEmailAsync(email, "Password changed", "Your password has been updated successfully.");
    }

    // Inner class for temporary verification code
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