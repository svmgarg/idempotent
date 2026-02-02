package com.idempotent.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for IdempotencyRequest validation constraints.
 */
class IdempotencyRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should pass validation for valid request")
    void shouldPassValidationForValidRequest() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("valid-key-123")
                .clientId("client-abc")
                .ttlSeconds(3600L)
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when idempotency key is null")
    void shouldFailValidationWhenKeyIsNull() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(null)
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Idempotency key is required");
    }

    @Test
    @DisplayName("Should fail validation when idempotency key is empty")
    void shouldFailValidationWhenKeyIsEmpty() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("")
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("Should fail validation when idempotency key is blank")
    void shouldFailValidationWhenKeyIsBlank() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("   ")
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("Should fail validation when idempotency key exceeds max length")
    void shouldFailValidationWhenKeyExceedsMaxLength() {
        String longKey = "a".repeat(257);
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(longKey)
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("must be between 1 and 256 characters");
    }

    @Test
    @DisplayName("Should pass validation when idempotency key is at max length")
    void shouldPassValidationWhenKeyAtMaxLength() {
        String maxKey = "a".repeat(256);
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey(maxKey)
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when clientId exceeds max length")
    void shouldFailValidationWhenClientIdExceedsMaxLength() {
        String longClientId = "c".repeat(129);
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("valid-key")
                .clientId(longClientId)
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("must not exceed 128 characters");
    }

    @Test
    @DisplayName("Should pass validation with null clientId")
    void shouldPassValidationWithNullClientId() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("valid-key")
                .clientId(null)
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should pass validation with null ttlSeconds")
    void shouldPassValidationWithNullTtl() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("valid-key")
                .ttlSeconds(null)
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should pass validation with special characters in key")
    void shouldPassValidationWithSpecialCharactersInKey() {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .idempotencyKey("key-with-special_chars@#$%123")
                .build();

        Set<ConstraintViolation<IdempotencyRequest>> violations = validator.validate(request);
        
        assertThat(violations).isEmpty();
    }
}
