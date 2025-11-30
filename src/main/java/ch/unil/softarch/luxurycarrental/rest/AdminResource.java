package ch.unil.softarch.luxurycarrental.rest;

import ch.unil.softarch.luxurycarrental.domain.entities.Admin;
import ch.unil.softarch.luxurycarrental.service.AdminService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/admins")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    private AdminService adminService;

    // Get all admins
    @GET
    public List<Admin> getAllAdmins() {
        return adminService.getAllAdmins();
    }

    // Get one admin by ID
    @GET
    @Path("/{id}")
    public Admin getAdmin(@PathParam("id") UUID id) {
        return adminService.getAdmin(id);
    }

    // Add new admin
    @POST
    public Admin addAdmin(Admin admin) {
        return adminService.addAdmin(admin);
    }

    // Update existing admin
    @PUT
    @Path("/{id}")
    public Admin updateAdmin(@PathParam("id") UUID id, Admin update) {
        return adminService.updateAdmin(id, update);
    }

    // Delete admin
    @DELETE
    @Path("/{id}")
    public boolean removeAdmin(@PathParam("id") UUID id) {
        return adminService.removeAdmin(id);
    }

    // Login
    @POST
    @Path("/login")
    public Response loginAdmin(Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        if (email == null || password == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Email and password are required")
                    .build();
        }

        try {
            Admin admin = adminService.authenticate(email, password);
            return Response.ok(admin).build();
        } catch (WebApplicationException e) {
            return Response.status(e.getResponse().getStatus())
                    .entity(e.getMessage())
                    .build();
        }
    }

    /**
     * Request a password reset code to be sent to admin's email
     */
    @POST
    @Path("/password-reset-code")
    public Response sendPasswordResetCodeByEmail(Map<String, String> body) {
        String email = body.get("email");
        if (email == null) {
            throw new WebApplicationException("Email is required", 400);
        }

        adminService.sendAdminPasswordResetCodeByEmail(email);
        return Response.ok(Map.of("message", "Verification code sent to admin email")).build();
    }

    /**
     * Reset admin password using verification code
     */
    @PUT
    @Path("/reset-password")
    public Response resetPasswordWithCodeByEmail(Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");

        if (email == null || code == null || newPassword == null) {
            throw new WebApplicationException("Missing required fields", 400);
        }

        adminService.resetAdminPasswordWithCodeByEmail(email, code, newPassword);
        return Response.ok(Map.of("message", "Admin password reset successfully")).build();
    }

}