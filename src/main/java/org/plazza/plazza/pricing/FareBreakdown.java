package org.plazza.plazza.pricing;

import org.plazza.plazza.common.enums.CarType;

import java.math.BigDecimal;

/**
 * Every stage of the fare calculation, not just the answer.
 * <p>
 * Keeping the intermediate values means a failing test names the exact stage that broke
 * ("surge was right, the minimum-fare floor was not") and the demo can show the arithmetic
 * rather than assert a single opaque number. The values are persisted onto the ride row, so a
 * completed ride carries its own audit trail.
 *
 * @param distanceKm      billed distance
 * @param billedCarType   the rate card used — on a free upgrade this is the car type the rider
 *                        <em>requested</em>, not the one that turned up
 * @param baseFare        slab total after the minimum-fare floor
 * @param surgeMultiplier 1.00 when surge is off
 * @param fareAfterSurge  {@code baseFare x surgeMultiplier}
 * @param discount        coupon discount actually granted, never more than {@code fareAfterSurge}
 * @param total           what the rider pays
 */
public record FareBreakdown(double distanceKm,
                            CarType billedCarType,
                            BigDecimal baseFare,
                            BigDecimal surgeMultiplier,
                            BigDecimal fareAfterSurge,
                            BigDecimal discount,
                            BigDecimal total) {
}
