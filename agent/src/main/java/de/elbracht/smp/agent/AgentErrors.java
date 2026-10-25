package de.elbracht.smp.agent;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentErrors {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> invalid() {
        // Keine Rohpayloads oder Spring-Validierungsdetails loggen/zurückgeben.
        return ResponseEntity.unprocessableEntity().body(Map.of("code", "INVALID_REQUEST"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> failed() { return ResponseEntity.internalServerError().body(Map.of("code", "AGENT_INTERNAL_ERROR")); }
}
