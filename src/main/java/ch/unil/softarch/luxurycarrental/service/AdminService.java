package ch.unil.softarch.luxurycarrental.service;

import ch.unil.softarch.luxurycarrental.domain.entities.Admin;
import ch.unil.softarch.luxurycarrental.service.EmailSender; // Assuming this is your email utility
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
 * Service class for managing Administrator accounts.
 * <p>
 * This class handles the business logic for creating, retrieving, updating, and deleting admins.
 * It interacts with the database using the Jakarta Persistence API (JPA) {@link EntityManager}.
 * </p>
 */
@ApplicationScoped
public class AdminService {

    /**
     * The Entity Manager used to interact with the persistence context (Database).
     * The unitName must match the one defined in your persistence.xml.
     */
    @PersistenceContext(unitName = "LuxuryCarRentalPU")
    private EntityManager em;

    // Temporary in-memory storage for password reset codes.
    // In a production environment, it is recommended to store these codes in the database (e.g., in the Admin entity).
    private final Map<String, VerificationCode> passwordResetCodes = new ConcurrentHashMap<>();

    /**
     * Inner class to represent a verification code with a timestamp.
     */
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
     * Registers a new administrator in the system.
     *
     * @param admin The admin entity to persist.
     * @return The persisted admin entity.
     * @throws WebApplicationException if the username or email already exists.
     */
    @Transactional // Required for write operations (INSERT)
    public Admin addAdmin(Admin admin) {

        // NOTE ON PERSISTENCE:
        // The ID, createdAt, and updatedAt fields must be set by the Admin entity's @PrePersist
        // callback to satisfy NOT NULL constraints before the transaction commits.

        // 1. Check if the username already exists.
        Long usernameCount = em.createQuery("SELECT COUNT(a) FROM Admin a WHERE a.username = :username", Long.class)
                .setParameter("username", admin.getUsername())
                .getSingleResult();
        if (usernameCount > 0) {
            throw new WebApplicationException("Username already exists", 400);
        }

        // 2. Check if the email already exists.
        Long emailCount = em.createQuery("SELECT COUNT(a) FROM Admin a WHERE a.email = :email", Long.class)
                .setParameter("email", admin.getEmail())
                .getSingleResult();
        if (emailCount > 0) {
            throw new WebApplicationException("Email already exists", 400);
        }

        // 3. Persist the new entity.
        em.persist(admin);

        // 4. Send a welcome email asynchronously (independent of the transaction status).
        sendWelcomeEmail(admin);

        return admin;
    }

    /**
     * Helper method to send a welcome email asynchronously to avoid blocking the transaction.
     */
    private void sendWelcomeEmail(Admin admin) {
        new Thread(() -> {
            try {
                String to = admin.getEmail();
                String subject = "Welcome to Luxury Car Rental Admin Panel";
                String body = "Hi " + admin.getName() + ",\n\n" +
                        "Your administrator account has been successfully created.\n" +
                        "Username: " + admin.getUsername() + "\n" +
                        "Please keep your password secure.\n\n" +
                        "Best regards,\nLuxury Car Rental Team";
                EmailSender.sendEmailAsync(to, subject, body);
            } catch (Exception e) {
                e.printStackTrace(); // Log error in production
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Retrieves an admin by their unique ID.
     *
     * @param id The UUID of the admin.
     * @return The found Admin entity.
     * @throws WebApplicationException if no admin is found.
     */
    public Admin getAdmin(UUID id) {
        // Uses EntityManager.find() for efficient primary key lookup.
        Admin admin = em.find(Admin.class, id);
        if (admin == null) throw new WebApplicationException("Admin not found", 404);
        return admin;
    }

    /**
     * Retrieves all administrators from the database.
     *
     * @return A list of all Admin entities.
     */
    public List<Admin> getAllAdmins() {
        // Uses JP-QL to select all records from the Admin table.
        return em.createQuery("SELECT a FROM Admin a", Admin.class).getResultList();
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Updates an existing administrator's details.
     *
     * @param id     The ID of the admin to update.
     * @param update An Admin object containing the new values.
     * @return The updated Admin entity.
     */
    @Transactional // Required for write operations (UPDATE)
    public Admin updateAdmin(UUID id, Admin update) {
        // 1. Find the existing entity. It becomes "Managed" by the persistence context.
        Admin existing = em.find(Admin.class, id);
        if (existing == null) throw new WebApplicationException("Admin not found", 404);

        // 2. Update fields if they are provided.
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getUsername() != null) existing.setUsername(update.getUsername());
        if (update.getEmail() != null) existing.setEmail(update.getEmail());
        if (update.getPassword() != null && !update.getPassword().isBlank()) {
            existing.setPassword(update.getPassword());
        }

        // 3. Automatic Dirty Checking:
        // Because 'existing' is in the "Managed" state, JPA detects changes automatically.
        // The UPDATE SQL statement will be generated automatically when the transaction commits.
        // No explicit em.merge() or save() call is strictly necessary here.

        return existing;
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Removes an administrator from the system.
     *
     * @param id The ID of the admin to delete.
     * @return true if deleted successfully, false if not found.
     */
    @Transactional // Required for write operations (DELETE)
    public boolean removeAdmin(UUID id) {
        Admin admin = em.find(Admin.class, id);
        if (admin != null) {
            em.remove(admin);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    /**
     * Authenticates an admin using email and password.
     *
     * @param email    The login email.
     * @param password The login password.
     * @return The authenticated Admin entity.
     * @throws WebApplicationException if authentication fails.
     */
    public Admin authenticate(String email, String password) {
        try {
            // Uses a typed JP-QL query to find a matching user.
            TypedQuery<Admin> query = em.createQuery(
                    "SELECT a FROM Admin a WHERE a.email = :email AND a.password = :password",
                    Admin.class);
            query.setParameter("email", email);
            query.setParameter("password", password);

            // Returns the single result or throws NoResultException if none found.
            return query.getSingleResult();
        } catch (Exception e) {
            throw new WebApplicationException("Invalid email or password", 401);
        }
    }

    // -------------------------------------------------------------------------
    // Password Reset Logic
    // -------------------------------------------------------------------------

    /**
     * Generates a verification code and sends it to the admin's email.
     *
     * @param email The email address of the admin.
     */
    public void sendAdminPasswordResetCodeByEmail(String email) {
        // 1. Verify the admin exists in the database.
        Admin admin;
        try {
            admin = em.createQuery("SELECT a FROM Admin a WHERE a.email = :email", Admin.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (Exception e) {
            throw new WebApplicationException("Admin account not found", 404);
        }

        // 2. Generate a random 6-digit code.
        String code = String.format("%06d", new Random().nextInt(999999));

        // 3. Store the code in the temporary in-memory map.
        passwordResetCodes.put(admin.getEmail(), new VerificationCode(code, LocalDateTime.now()));

        // 4. Send the code via email.
        String subject = "Admin Password Reset Code";
        String body = "Dear " + admin.getName() + ",\n\n" +
                "Your password reset code is: " + code + "\n" +
                "This code will expire in 5 minutes.\n";

        EmailSender.sendEmailAsync(admin.getEmail(), subject, body);
    }

    /**
     * Verifies the code and updates the admin's password.
     *
     * @param email       The email address.
     * @param code        The verification code received.
     * @param newPassword The new password to set.
     */
    @Transactional // Required because we are updating the password in the database
    public void resetAdminPasswordWithCodeByEmail(String email, String code, String newPassword) {
        // 1. Retrieve the admin entity (Managed state).
        Admin admin;
        try {
            admin = em.createQuery("SELECT a FROM Admin a WHERE a.email = :email", Admin.class)
                    .setParameter("email", email)
                    .getSingleResult();
        } catch (Exception e) {
            throw new WebApplicationException("Admin account not found", 404);
        }

        // 2. Validate the code from the in-memory map.
        VerificationCode stored = passwordResetCodes.get(email);
        if (stored == null) {
            throw new WebApplicationException("No verification code found", 400);
        }

        // Check for expiration (5 minutes).
        if (Duration.between(stored.createdAt, LocalDateTime.now()).toMinutes() >= 5) {
            passwordResetCodes.remove(email);
            throw new WebApplicationException("Verification code has expired", 400);
        }

        // Check if code matches.
        if (!stored.code.equals(code)) {
            throw new WebApplicationException("Invalid verification code", 400);
        }

        // 3. Update the password.
        // Since 'admin' is a managed entity within a transaction, this change is
        // automatically persisted to the database upon method completion.
        admin.setPassword(newPassword);

        // 4. Clean up the used code.
        passwordResetCodes.remove(email);

        // 5. Send confirmation email.
        EmailSender.sendEmailAsync(email, "Password Changed", "Your password has been changed.");
    }
}