package org.plazza.plazza.coupon.internal.policy;

import org.plazza.plazza.coupon.CouponType;
import org.plazza.plazza.coupon.internal.CouponEntity;

import java.math.BigDecimal;

/**
 * Computes the discount for one kind of coupon.
 * <p>
 * One implementation per {@link CouponType}, selected by {@link #supports}. A new kind of coupon is
 * a new class picked up by component scanning — no switch statement anywhere gains a case, and the
 * fare pipeline is untouched.
 */
public interface DiscountPolicy {

    boolean supports(CouponType type);

    /**
     * Discount for the given fare. Implementations return their own natural cap; the fare pipeline
     * clamps the result to the fare regardless, so no policy can produce a negative total.
     */
    BigDecimal discountFor(CouponEntity coupon, BigDecimal fare);
}
