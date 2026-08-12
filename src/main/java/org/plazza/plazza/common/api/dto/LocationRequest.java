package org.plazza.plazza.common.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.plazza.plazza.common.geo.Location;

/**
 * A coordinate arriving over HTTP.
 * <p>
 * Bean validation catches the common mistakes at the edge (missing field, swapped lat/lng putting
 * a longitude in the latitude slot), and {@link Location} re-checks the ranges on construction, so
 * a bad coordinate can never reach the distance calculation.
 */
public record LocationRequest(
        @NotNull(message = "lat is required")
        @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
        Double lat,

        @NotNull(message = "lng is required")
        @DecimalMin(value = "-180.0", message = "lng must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "lng must be between -180 and 180")
        Double lng) {

    public Location toLocation() {
        return Location.of(lat, lng);
    }
}
