package org.plazza.plazza.common.geo;

import org.plazza.plazza.common.error.ValidationException;

import java.math.BigDecimal;

/**
 * An immutable WGS-84 coordinate. Validated at construction so an out-of-range latitude fails at
 * the edge of the system rather than producing a silently wrong distance deep in the fare pipeline.
 */
public record Location(double lat, double lng) {

    public Location {
        if (lat < -90 || lat > 90) {
            throw new ValidationException("latitude must be between -90 and 90, got " + lat);
        }
        if (lng < -180 || lng > 180) {
            throw new ValidationException("longitude must be between -180 and 180, got " + lng);
        }
    }

    public static Location of(double lat, double lng) {
        return new Location(lat, lng);
    }

    /** Rebuilds a location from the {@code DECIMAL(9,6)} columns it was persisted into. */
    public static Location of(BigDecimal lat, BigDecimal lng) {
        return new Location(lat.doubleValue(), lng.doubleValue());
    }

    public BigDecimal latAsDecimal() {
        return BigDecimal.valueOf(lat);
    }

    public BigDecimal lngAsDecimal() {
        return BigDecimal.valueOf(lng);
    }
}
