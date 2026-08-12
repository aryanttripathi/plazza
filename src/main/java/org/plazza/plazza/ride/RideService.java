package org.plazza.plazza.ride;

import org.plazza.plazza.pricing.FareBreakdown;

import java.util.List;

/**
 * Ride orchestration: it decides <em>what happens</em>, never <em>what anything costs</em>.
 * <p>
 * Every number this module returns came from {@code pricing}; every choice of driver came from
 * {@code matching}. That separation is what makes each of those swappable in isolation.
 */
public interface RideService {

    /**
     * Reserves a driver and starts a ride.
     *
     * @throws org.plazza.plazza.common.error.NoDriverAvailableException when nobody is in radius, or
     *         every ranked candidate was taken by a concurrent booking
     * @throws org.plazza.plazza.common.error.DuplicateActiveRideException when the rider already has
     *         an ongoing ride
     */
    RideView book(BookRideCommand command);

    /** Ends an ongoing ride, prices it, and returns the driver to the pool. */
    FareBreakdown endRide(String rideId);

    RideView requireById(String rideId);

    /** @param status optional filter; {@code null} returns ongoing and completed rides alike */
    List<RideView> historyForUser(String userId, RideStatus status);

    List<RideView> historyForDriver(String driverId, RideStatus status);
}
