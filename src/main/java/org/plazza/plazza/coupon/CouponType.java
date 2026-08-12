package org.plazza.plazza.coupon;

/**
 * How a coupon computes its discount. Each constant is served by one
 * {@code DiscountPolicy} implementation, so a new kind of coupon is a new class
 * plus a constant — never a branch inside the fare pipeline.
 */
public enum CouponType {

    /** {@code value} is a percentage of the fare, optionally limited by {@code maxDiscount}. */
    PERCENT,

    /** {@code value} is a flat rupee amount, never more than the fare itself. */
    FLAT
}
