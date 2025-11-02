package ch.unil.softarch.luxurycarrental.rest;

import ch.unil.softarch.luxurycarrental.domain.exceptions.BookingConflictException;
import ch.unil.softarch.luxurycarrental.domain.exceptions.CarUnavailableException;
import ch.unil.softarch.luxurycarrental.domain.exceptions.PaymentFailedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.core.UriInfo;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Global exception handler for the REST API.
 * Maps exceptions from the domain or service layer into appropriate HTTP responses with extra details.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Context
    private UriInfo uriInfo; // Provides request URI information

    @Override
    public Response toResponse(Exception exception) {
        log.severe("Exception caught: " + exception.getMessage());

        // Default HTTP status code
        Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
        String message = exception.getMessage();

        // Map specific domain exceptions to proper HTTP status codes
        if (exception instanceof CarUnavailableException) {
            status = Response.Status.CONFLICT; // 409 Conflict
            message = "The selected car is currently unavailable.";
        } else if (exception instanceof BookingConflictException) {
            status = Response.Status.CONFLICT; // 409 Conflict
            message = "A booking conflict occurred. Please select another time.";
        } else if (exception instanceof PaymentFailedException) {
            status = Response.Status.PAYMENT_REQUIRED; // 402 Payment Required
            message = "Payment failed. Please check your payment method.";
        }

        // Build a unified error response
        ErrorResponse errorResponse = new ErrorResponse(
                exception.getClass().getSimpleName(),
                message,
                LocalDateTime.now(),
                uriInfo != null ? uriInfo.getPath() : ""
        );

        return Response.status(status)
                .entity(errorResponse)
                .build();
    }

    /**
     * Unified error response model.
     * Sent back to clients as a JSON object.
     */
    public static class ErrorResponse {
        private String errorType;      // Type of the exception
        private String message;        // Human-readable error message
        private LocalDateTime timestamp; // Time when the error occurred
        private String path;           // Request path that triggered the exception

        public ErrorResponse(String errorType, String message, LocalDateTime timestamp, String path) {
            this.errorType = errorType;
            this.message = message;
            this.timestamp = timestamp;
            this.path = path;
        }

        public String getErrorType() { return errorType; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getPath() { return path; }
    }
}