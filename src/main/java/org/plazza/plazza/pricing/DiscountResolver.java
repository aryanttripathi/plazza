package org.plazza.plazza.pricing;

import java.math.BigDecimal;

/**
 * How much to knock off a fare, given the fare the discount applies to.
 * <p>
 * The pricing engine owns the <em>ordering</em> rule — a discount is computed against the fare
 * <em>after</em> surge, never before — while the coupon module owns the discount arithmetic. Passing
 * this callback into {@link FareCalculator} is what keeps both facts in one place each: callers
 * cannot accidentally apply a coupon at the wrong stage, and the pricing module never learns what
 * a coupon is.
 */
@FunctionalInterface
public interface DiscountResolver {

    /** Discount for the given post-surge fare. Implementations must never return more than it. */
    BigDecimal discountFor(BigDecimal fareAfterSurge);

    /** No coupon on this ride. */
    static DiscountResolver none() {
        return fare -> BigDecimal.ZERO;
    }
}
