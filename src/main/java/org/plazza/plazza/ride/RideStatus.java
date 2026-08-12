package org.plazza.plazza.ride;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

/** Ride lifecycle. Both terminal states release the driver back into the available pool. */
public enum RideStatus {

    ONGOING,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this != ONGOING;
    }

    /**
     * Null-safe, case-insensitive parse for the {@code ?status=} history filter.
     *
     * @return the matching constant, or {@code null} when the input is blank or unrecognised
     */
    public static RideStatus parseOrNull(String raw) {
        return EnumUtils.getEnumIgnoreCase(RideStatus.class, StringUtils.trimToNull(raw));
    }
}
