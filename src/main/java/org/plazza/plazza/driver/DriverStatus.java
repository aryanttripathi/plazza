package org.plazza.plazza.driver;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Driver availability.
 * <p>
 * Transitions happen only through conditional {@code UPDATE ... WHERE status = ?} statements — never
 * a read followed by a write — which is what makes it impossible for two concurrent bookings to
 * reserve the same driver.
 */
public enum DriverStatus {

    /** Online and reservable. */
    AVAILABLE,

    /** Reserved for an ongoing ride. */
    ON_TRIP,

    /** Online status withdrawn by the driver; never a matching candidate. */
    OFFLINE;

    /**
     * Null-safe, case-insensitive parse for values arriving over HTTP.
     *
     * @return the matching constant, or {@code null} when the input is blank or unrecognised
     */
    public static DriverStatus parseOrNull(String raw) {
        return EnumUtils.getEnumIgnoreCase(DriverStatus.class, StringUtils.trimToNull(raw));
    }
}
