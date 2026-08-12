package org.plazza.plazza.common.error;

import org.springframework.http.HttpStatus;

/** A ride transition was attempted from a state that does not allow it, e.g. ending a completed ride. */
public class IllegalRideStateException extends DomainException {

    public IllegalRideStateException(String rideId, Object current, String attempted) {
        super("ILLEGAL_RIDE_STATE",
              HttpStatus.CONFLICT,
              "ride " + rideId + " is " + current + " and cannot be " + attempted);
    }
}
