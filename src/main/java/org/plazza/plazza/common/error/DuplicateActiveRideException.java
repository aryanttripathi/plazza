package org.plazza.plazza.common.error;

import org.springframework.http.HttpStatus;

/**
 * Raised when the {@code uk_active_user} / {@code uk_active_driver} unique index rejects a second
 * ongoing ride. The database is what actually enforces this, so the constraint violation is
 * translated here rather than being pre-empted by a check-then-insert race in service code.
 */
public class DuplicateActiveRideException extends DomainException {

    public DuplicateActiveRideException(String subject, String id) {
        super("DUPLICATE_ACTIVE_RIDE",
              HttpStatus.CONFLICT,
              subject + " " + id + " already has an ongoing ride");
    }
}
