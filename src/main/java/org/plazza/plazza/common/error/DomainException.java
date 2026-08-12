package org.plazza.plazza.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for every expected business failure.
 * <p>
 * Each subclass carries its own stable {@code code} and HTTP status, so {@link GlobalExceptionHandler}
 * is a single generic mapping rather than a growing chain of {@code instanceof} checks: adding a new
 * failure mode means adding a subclass, not editing the handler.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    /** Stable, machine-readable identifier returned to clients, for example NO_DRIVER_AVAILABLE. */
    private final String code;

    private final HttpStatus status;

    protected DomainException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
