package org.plazza.plazza.coupon;

import java.math.BigDecimal;
import java.util.List;

/**
 * Coupon lifecycle and discount lookup.
 * <p>
 * This module owns what a discount <em>is</em>. It does not own <em>when</em> one applies: the fare
 * pipeline decides that a coupon is evaluated against the post-surge fare, and calls back into here
 * through {@code DiscountResolver}.
 */
public interface CouponService {

    CouponView add(CreateCouponCommand command);

    /** @throws org.plazza.plazza.common.error.NotFoundException when the code does not exist */
    void delete(String code);

    CouponView requireByCode(String code);

    List<CouponView> findAll();

    /**
     * Checks a coupon is usable right now.
     *
     * @throws org.plazza.plazza.common.error.InvalidCouponException when unknown, inactive or expired
     */
    void validate(String code);

    /**
     * Discount for a fare, or zero when the code is blank.
     * <p>
     * Returns zero rather than throwing when a previously valid coupon has since been deleted or
     * expired, so a rider can always end their ride — see the note on {@code RideServiceImpl}.
     */
    BigDecimal discountFor(String code, BigDecimal fare);
}
