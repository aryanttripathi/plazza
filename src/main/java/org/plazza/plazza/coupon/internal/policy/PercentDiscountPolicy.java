package org.plazza.plazza.coupon.internal.policy;

import org.plazza.plazza.common.money.Money;
import org.plazza.plazza.coupon.CouponType;
import org.plazza.plazza.coupon.internal.CouponEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * A percentage off the fare, optionally capped.
 * <p>
 * The cap is what makes "20% off" safe on a long airport run: without {@code maxDiscount} the
 * discount grows with the fare forever.
 */
@Component
public class PercentDiscountPolicy implements DiscountPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public boolean supports(CouponType type) {
        return type == CouponType.PERCENT;
    }

    @Override
    public BigDecimal discountFor(CouponEntity coupon, BigDecimal fare) {
        BigDecimal raw = Money.scaled(fare.multiply(coupon.getValue()).divide(HUNDRED, 10, Money.ROUNDING));

        return coupon.getMaxDiscount() == null
                ? raw
                : Money.min(raw, coupon.getMaxDiscount());
    }
}
