package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pt.ua.deti.tqs.tuttibox.core.service.DomainRuleException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainRuleException.class)
    public ProblemDetail handleDomainRuleException(DomainRuleException ex) {
        HttpStatus status = determineHttpStatus(ex.getErrorCode());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setType(URI.create("https://tuttibox.pt/errors/" + ex.getErrorCode().toLowerCase()));
        problem.setTitle(formatTitle(ex.getErrorCode()));
        problem.setProperty("errorCode", ex.getErrorCode());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for request parameters.");
        problem.setType(URI.create("https://tuttibox.pt/errors/validation-failed"));
        problem.setTitle("Bad Request");

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        problem.setProperty("invalidFields", fieldErrors);
        return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
    }

    private HttpStatus determineHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "NO_LOCKERS_AVAILABLE", "INVALID_STATE", "DROPOFF_EXPIRED", "PICKUP_EXPIRED", "COMPARTMENT_NOT_FREE" ->
                    HttpStatus.CONFLICT; // 409
            case "STATION_NOT_FOUND", "PARCEL_NOT_FOUND", "COMPARTMENT_NOT_FOUND", "GEOCODING_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND; // 404
            case "TERMINAL_LOCKED" ->
                    HttpStatus.TOO_MANY_REQUESTS; // 429
            case "INVALID_PIN" ->
                    HttpStatus.UNAUTHORIZED; // 401
            case "DOOR_UNLOCK_FAILED", "DOOR_JAMMED" ->
                    HttpStatus.INTERNAL_SERVER_ERROR; // 500
            default -> HttpStatus.BAD_REQUEST; // 400
        };
    }

    private String formatTitle(String errorCode) {
        return switch (errorCode) {
            case "NO_LOCKERS_AVAILABLE" -> "No Lockers Available";
            case "INVALID_STATE" -> "Invalid State Transition";
            case "STATION_NOT_FOUND" -> "Station Not Found";
            case "PARCEL_NOT_FOUND" -> "Parcel Not Found";
            case "DROPOFF_EXPIRED" -> "Drop-off Deadline Expired";
            case "PICKUP_EXPIRED" -> "Pickup Deadline Expired";
            case "TERMINAL_LOCKED" -> "Terminal Lockout Active";
            case "INVALID_PIN" -> "Invalid Pickup PIN";
            case "DOOR_JAMMED" -> "Compartment Door Jammed";
            case "DOOR_UNLOCK_FAILED" -> "Hardware Unlock Failed";
            default -> "Business Rule Violation";
        };
    }
}
