package de.elbracht.smp.api;

import java.util.UUID;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrors {
    public record Error(String code, String message, String traceId) {}
    private ResponseEntity<Error> response(int status, String code, String message) {
        return ResponseEntity.status(status).body(new Error(code, message, UUID.randomUUID().toString()));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> domain(ApiException e) { return response(e.status, e.code, e.getMessage()); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Error> denied() { return response(403, "FORBIDDEN", "Keine Berechtigung für diesen Server."); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Error> missing() { return response(404, "NOT_FOUND", "Endpunkt nicht vorhanden."); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HttpMessageNotReadableException.class, ServletRequestBindingException.class,
        HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> invalid() { return response(422, "INVALID_REQUEST", "Anfrage oder Parameter sind ungültig."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected() { return response(500, "INTERNAL_ERROR", "Interner Fehler. Bitte erneut versuchen."); }
}
