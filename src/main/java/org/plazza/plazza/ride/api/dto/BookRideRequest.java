package org.plazza.plazza.ride.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.plazza.plazza.common.api.dto.LocationRequest;

/**
 * @param radiusKm   optional; omitted means the configured default search radius
 * @param couponCode optional; whitespace and case are normalised, so " save20 " finds SAVE20
 */
public record BookRideRequest(
        @NotBlank(message = "userId is required")
        String userId,

        @NotNull(message = "pickup is required")
        @Valid
        LocationRequest pickup,

        @NotNull(message = "drop is required")
        @Valid
        LocationRequest drop,

        @NotBlank(message = "carType is required")
        String carType,

        @Positive(message = "radiusKm must be positive")
        Double radiusKm,

        String couponCode) {
}
