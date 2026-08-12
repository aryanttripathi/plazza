package org.plazza.plazza.coupon.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code type} is a String so an unknown value produces our own 400 naming the valid options,
 * rather than a Jackson deserialisation error.
 */
public record CreateCouponRequest(
        @NotBlank(message = "code is required")
        @Size(max = 40, message = "code must be at most 40 characters")
        String code,

        @NotBlank(message = "type is required")
        String type,

        @Positive(message = "value must be positive")
        BigDecimal value,

        @Positive(message = "maxDiscount must be positive")
        BigDecimal maxDiscount,

        Instant expiresAt) {
}
