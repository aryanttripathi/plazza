package org.plazza.plazza.pricing.surge.internal;

import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.pricing.surge.SurgeStrategy;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Surge disabled: every ride is priced at 1.00x.
 * <p>
 * Registered as a fallback bean (see {@code PricingConfig}) that steps aside as soon as a real
 * {@link SurgeStrategy} is defined, so enabling surge never means editing the fare pipeline or
 * adding a null check to it.
 */
public class NoSurgeStrategy implements SurgeStrategy {

    private static final BigDecimal NO_SURGE = BigDecimal.ONE.setScale(2);

    @Override
    public BigDecimal multiplier(Location pickup, Instant when) {
        return NO_SURGE;
    }
}
