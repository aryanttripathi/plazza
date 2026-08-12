package org.plazza.plazza.coupon;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param maxDiscount ceiling for a PERCENT coupon; {@code null} means uncapped
 * @param expiresAt   {@code null} means the coupon never expires
 */
public record CreateCouponCommand(String code,
                                  CouponType type,
                                  BigDecimal value,
                                  BigDecimal maxDiscount,
                                  Instant expiresAt) {
}
