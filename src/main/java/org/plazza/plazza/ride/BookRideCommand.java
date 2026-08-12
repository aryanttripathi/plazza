package org.plazza.plazza.ride;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.geo.Location;

/**
 * A booking request.
 *
 * @param radiusKm   how far to look for a driver; {@code null} falls back to the configured default
 * @param couponCode optional; validated at booking and applied when the ride ends and the fare exists
 */
public record BookRideCommand(String userId,
                              Location pickup,
                              Location drop,
                              CarType carType,
                              Double radiusKm,
                              String couponCode) {
}
