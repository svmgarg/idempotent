package com.idempotent.exception;

import com.idempotent.dto.IdempotencyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation exceptions and returns 200 OK with error details
     * for Zapier compatibility (Zapier treats non-2xx as failures)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<IdempotencyResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error: {}", errors);

        // Return 200 OK with error details for Zapier compatibility
        IdempotencyResponse response = IdempotencyResponse.builder()
                .resultStatusCode(400)
                .message("Validation Failed")
                .validationErrors(errors)
                .build();

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<IdempotencyResponse> handleRuntimeException(RuntimeException ex) {
        log.error("Unexpected error", ex);

        // Return 200 OK with error details for Zapier compatibility
        IdempotencyResponse response = IdempotencyResponse.builder()
                .resultStatusCode(500)
                .message("Internal Server Error")
                .build();

        return ResponseEntity.ok(response);
    }
}
