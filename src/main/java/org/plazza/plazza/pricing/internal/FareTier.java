package org.plazza.plazza.pricing.internal;

import java.math.BigDecimal;

/**
 * One pricing slab, half-open over {@code [fromKm, toKm)}.
 *
 * @param fromKm    inclusive lower bound
 * @param toKm      exclusive upper bound
 * @param ratePerKm rupees per kilometre inside this slab
 */
public record FareTier(double fromKm, double toKm, BigDecimal ratePerKm) {

    /**
     * How many of the ride's kilometres fall inside this slab.
     * <p>
     * Clamping to zero is what makes the tier list order-independent and safe for trips shorter
     * than the slab: a 3 km ride simply contributes nothing to the 5 km-and-beyond tier.
     */
    public double billableKm(double distanceKm) {
        return Math.max(0.0, Math.min(distanceKm, toKm) - fromKm);
    }

    /** This slab's contribution to the fare, at full precision — rounding happens once, at the end. */
    public BigDecimal chargeFor(double distanceKm) {
        return BigDecimal.valueOf(billableKm(distanceKm)).multiply(ratePerKm);
    }
}
