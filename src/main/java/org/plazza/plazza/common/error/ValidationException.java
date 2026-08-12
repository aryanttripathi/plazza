package org.plazza.plazza.common.error;

import org.springframework.http.HttpStatus;

/** Input that failed a domain-level check that bean validation cannot express on its own. */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
