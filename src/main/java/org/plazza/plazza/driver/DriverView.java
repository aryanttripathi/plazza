package org.plazza.plazza.driver;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.geo.GeoUtils;
import org.plazza.plazza.common.geo.Location;

import java.math.BigDecimal;

/**
 * What other modules are allowed to know about a driver — notably the matching strategies, which
 * rank candidates on {@link #location} and {@link #rating} without ever touching a JPA entity.
 */
public record DriverView(String id,
                         String name,
                         CarType carType,
                         BigDecimal rating,
                         Location location,
                         DriverStatus status) {

    /** Straight-line distance from this driver to a pickup point, in kilometres. */
    public double distanceKmTo(Location point) {
        return GeoUtils.distanceKm(location, point);
    }
}
