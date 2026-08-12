package org.plazza.plazza.common.error;

import org.springframework.http.HttpStatus;

/** A referenced entity does not exist. The {@code code} names which one, for example RIDE_NOT_FOUND. */
public class NotFoundException extends DomainException {

    public NotFoundException(String entity, String id) {
        super(entity.toUpperCase() + "_NOT_FOUND",
              HttpStatus.NOT_FOUND,
              entity + " not found: " + id);
    }
}
