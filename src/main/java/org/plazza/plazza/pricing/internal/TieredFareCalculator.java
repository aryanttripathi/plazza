package org.plazza.plazza.pricing.internal;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.money.Money;
import org.plazza.plazza.pricing.DiscountResolver;
import org.plazza.plazza.pricing.FareBreakdown;
import org.plazza.plazza.pricing.FareCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The pricing pipeline, in the fixed order the whole system depends on.
 * <p>
 * Every branch that could have been {@code if (carType == ...)} lives in the rate card instead, so
 * this class never changes when a car type or a slab is added.
 */
@Component
public class TieredFareCalculator implements FareCalculator {

    private final RateCardRegistry registry;

    public TieredFareCalculator(RateCardRegistry registry) {
        this.registry = registry;
    }

    @Override
    public FareBreakdown calculate(double distanceKm,
                                   CarType billedCarType,
                                   BigDecimal surgeMultiplier,
                                   DiscountResolver discountResolver) {

        if (distanceKm < 0 || Double.isNaN(distanceKm)) {
            throw new ValidationException("distance must be a non-negative number, got " + distanceKm);
        }
        if (surgeMultiplier == null || surgeMultiplier.signum() <= 0) {
            throw new ValidationException("surge multiplier must be positive, got " + surgeMultiplier);
        }

        RateCard card = registry.cardFor(billedCarType);

        // Slabs first, then the minimum-fare floor. Surge therefore multiplies the floor when the
        // floor is what applies, which is the behaviour the pricing spec describes.
        BigDecimal baseFare = card.baseFareFor(distanceKm);
        BigDecimal fareAfterSurge = Money.multiply(baseFare, surgeMultiplier);

        // The coupon sees the post-surge fare, and can never take more than it.
        BigDecimal requestedDiscount = Money.scaled(discountResolver.discountFor(fareAfterSurge));
        BigDecimal discount = clampDiscount(requestedDiscount, fareAfterSurge);

        BigDecimal total = Money.subtractToZero(fareAfterSurge, discount);

        return new FareBreakdown(distanceKm,
                billedCarType,
                Money.scaled(baseFare),
                Money.scaled(surgeMultiplier),
                fareAfterSurge,
                discount,
                total);
    }

    /**
     * A discount is never negative and never exceeds the fare, whatever a policy returns. Clamping
     * here rather than trusting each policy means one badly written coupon cannot produce a refund.
     */
    private static BigDecimal clampDiscount(BigDecimal discount, BigDecimal fareAfterSurge) {
        if (discount == null || discount.signum() < 0) {
            return Money.ZERO;
        }
        return Money.min(discount, fareAfterSurge);
    }
}
