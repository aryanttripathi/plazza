package org.plazza.plazza.common.enums;

import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Vehicle categories, ordered cheapest to most expensive.
 * <p>
 * The declaration order <em>is</em> the upgrade order: when a requested car type has no available
 * driver, booking looks for the next higher rank. Adding a category (for example SUV) therefore
 * means adding a constant here and a rate card in configuration — no branching logic changes.
 */
public enum CarType {

    HATCHBACK,
    SEDAN,
    SUV;

    /** Higher rank means a roomier, more expensive category. Based on declaration order. */
    public int rank() {
        return ordinal();
    }

    /** Every category strictly above this one, cheapest first — the free-upgrade candidates. */
    public List<CarType> upgradesAbove() {
        return Arrays.stream(values())
                .filter(type -> type.rank() > this.rank())
                .toList();
    }

    /**
     * Null-safe, case-insensitive parse for values arriving over HTTP.
     *
     * @return the matching constant, or {@code null} when the input is blank or unrecognised
     */
    public static CarType parseOrNull(String raw) {
        return EnumUtils.getEnumIgnoreCase(CarType.class, StringUtils.trimToNull(raw));
    }
}
