package org.plazza.plazza.pricing.internal;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.money.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * The price list for one car type: a minimum fare plus the distance slabs above it.
 */
public record RateCard(CarType carType, BigDecimal minimumFare, List<FareTier> tiers) {

    public RateCard {
        tiers = List.copyOf(tiers);
    }

    /**
     * Sum of every slab's contribution, then floored at the minimum fare.
     * <p>
     * The floor sits here, before surge and before any coupon: a 3 km sedan trip costs the ₹50
     * minimum rather than its ₹28 of slabs, and a 1.5x surge then applies to ₹50, not to ₹28.
     */
    public BigDecimal baseFareFor(double distanceKm) {
        BigDecimal slabTotal = tiers.stream()
                .map(tier -> tier.chargeFor(distanceKm))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Money.max(minimumFare, slabTotal);
    }

    /** Slab total with the minimum fare ignored — exposed so tests can assert the two separately. */
    public BigDecimal slabTotalFor(double distanceKm) {
        return Money.scaled(tiers.stream()
                .map(tier -> tier.chargeFor(distanceKm))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
