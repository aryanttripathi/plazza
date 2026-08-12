package org.plazza.plazza.coupon.internal.policy;

import org.plazza.plazza.common.money.Money;
import org.plazza.plazza.coupon.CouponType;
import org.plazza.plazza.coupon.internal.CouponEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * A flat rupee amount off the fare, never more than the fare itself — a 100 rupee coupon on a
 * 54 rupee ride makes the ride free, not a refund.
 */
@Component
public class FlatDiscountPolicy implements DiscountPolicy {

    @Override
    public boolean supports(CouponType type) {
        return type == CouponType.FLAT;
    }

    @Override
    public BigDecimal discountFor(CouponEntity coupon, BigDecimal fare) {
        return Money.min(coupon.getValue(), fare);
    }
}
