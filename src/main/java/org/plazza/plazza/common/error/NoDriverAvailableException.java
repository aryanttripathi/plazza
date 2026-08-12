package org.plazza.plazza.common.error;

import org.springframework.http.HttpStatus;

/**
 * No driver could be secured for the request: either none was within the radius, or every ranked
 * candidate lost its reservation race to a concurrent booking.
 */
public class NoDriverAvailableException extends DomainException {

    public NoDriverAvailableException(double radiusKm) {
        super("NO_DRIVER_AVAILABLE",
              HttpStatus.CONFLICT,
              "no driver available within " + radiusKm + " km");
    }
}
