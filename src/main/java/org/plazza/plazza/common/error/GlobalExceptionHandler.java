package org.plazza.plazza.common.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into the {@link ApiError} contract.
 * <p>
 * Domain failures are handled generically off {@link DomainException}, so a new failure mode is a
 * new subclass and this class stays closed for modification.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    /** Bean-validation failures on request DTOs, reported per field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.of("VALIDATION_FAILED", "request validation failed", fieldErrors));
    }

    /**
     * The active-ride unique indexes are enforced by MySQL, so a losing concurrent booking arrives
     * here as a constraint violation. Reporting it as 409 keeps the race an expected outcome rather
     * than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(DataIntegrityViolationException ex) {
        String detail = String.valueOf(ex.getMostSpecificCause().getMessage());
        String code = detail.contains("uk_active_user") || detail.contains("uk_active_driver")
                ? "DUPLICATE_ACTIVE_RIDE"
                : "CONSTRAINT_VIOLATION";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(code, detail));
    }
}
