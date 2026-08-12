package org.plazza.plazza.pricing;

import org.plazza.plazza.common.enums.CarType;

import java.math.BigDecimal;

/**
 * Turns a distance into a fare. The only place fare arithmetic lives.
 */
public interface FareCalculator {

    /**
     * Prices a ride through the full pipeline:
     * <pre>
     *   slab total -> floor at the minimum fare -> x surge -> - discount -> floor at zero
     * </pre>
     * The order is fixed here on purpose. Applying the minimum fare after surge, or a coupon before
     * surge, gives different money for the same trip; making that sequence a property of this method
     * rather than of its callers is what stops the two from drifting apart.
     *
     * @param billedCarType   rate card to price against — the <em>requested</em> car type, so a free
     *                        upgrade costs the rider nothing
     * @param surgeMultiplier from {@link org.plazza.plazza.pricing.surge.SurgeStrategy}; 1.00 for none
     * @param discountResolver evaluated against the post-surge fare
     */
    FareBreakdown calculate(double distanceKm,
                            CarType billedCarType,
                            BigDecimal surgeMultiplier,
                            DiscountResolver discountResolver);

    /** Quote with no surge and no coupon — used by the fare estimate endpoint. */
    default FareBreakdown quote(double distanceKm, CarType carType) {
        return calculate(distanceKm, carType, BigDecimal.ONE, DiscountResolver.none());
    }
}
