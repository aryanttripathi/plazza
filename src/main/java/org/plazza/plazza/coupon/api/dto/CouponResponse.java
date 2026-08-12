package org.plazza.plazza.coupon.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.plazza.plazza.coupon.CouponType;
import org.plazza.plazza.coupon.CouponView;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CouponResponse(String code,
                             CouponType type,
                             BigDecimal value,
                             BigDecimal maxDiscount,
                             Instant expiresAt,
                             boolean active) {

    public static CouponResponse from(CouponView view) {
        return new CouponResponse(view.code(),
                view.type(),
                view.value(),
                view.maxDiscount(),
                view.expiresAt(),
                view.active());
    }
}
