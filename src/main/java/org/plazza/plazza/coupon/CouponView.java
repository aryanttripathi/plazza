package org.plazza.plazza.coupon;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponView(String code,
                         CouponType type,
                         BigDecimal value,
                         BigDecimal maxDiscount,
                         Instant expiresAt,
                         boolean active) {
}
