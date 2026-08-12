package org.plazza.plazza.ride;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.pricing.FareBreakdown;

import java.time.Instant;

/**
 * A ride as seen from outside the module.
 *
 * @param assignedCarType the vehicle that actually took the trip
 * @param requestedCarType what the rider asked for, and what they are billed for
 * @param upgraded        true when those two differ — the free upgrade, visible as data
 * @param fare            {@code null} while the ride is ongoing; populated once it ends
 */
public record RideView(String id,
                       String userId,
                       String driverId,
                       CarType requestedCarType,
                       CarType assignedCarType,
                       boolean upgraded,
                       Location pickup,
                       Location drop,
                       RideStatus status,
                       FareBreakdown fare,
                       Instant startedAt,
                       Instant endedAt) {
}
