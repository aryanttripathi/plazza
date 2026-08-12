package org.plazza.plazza.pricing.surge;

import org.plazza.plazza.common.geo.Location;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The multiplier applied to a base fare for demand conditions at a place and time.
 * <p>
 * Pluggable on purpose: the shipped default returns 1.00, so surge is off without any caller
 * needing a null check or a feature flag. A real implementation reads demand against supply in the
 * pickup area; swapping it in changes no code in the fare pipeline, which only ever multiplies by
 * whatever number comes back.
 */
@FunctionalInterface
public interface SurgeStrategy {

    /** Multiplier for a pickup point at an instant. Must be positive; 1.00 means no surge. */
    BigDecimal multiplier(Location pickup, Instant when);
}
