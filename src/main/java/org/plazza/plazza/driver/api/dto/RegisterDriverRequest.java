package org.plazza.plazza.driver.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * {@code carType} is taken as a String rather than an enum so an unknown value produces a clear
 * 400 from our own validation, instead of Jackson failing to deserialise the body and reporting a
 * parse error the caller cannot act on.
 */
public record RegisterDriverRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotBlank(message = "carType is required")
        String carType,

        @NotNull(message = "rating is required")
        @DecimalMin(value = "0.0", message = "rating must be between 0 and 5")
        @DecimalMax(value = "5.0", message = "rating must be between 0 and 5")
        BigDecimal rating,

        @NotNull(message = "lat is required")
        @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
        Double lat,

        @NotNull(message = "lng is required")
        @DecimalMin(value = "-180.0", message = "lng must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "lng must be between -180 and 180")
        Double lng) {
}
