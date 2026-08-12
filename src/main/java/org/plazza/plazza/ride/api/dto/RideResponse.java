package org.plazza.plazza.ride.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.pricing.api.dto.FareResponse;
import org.plazza.plazza.ride.RideStatus;
import org.plazza.plazza.ride.RideView;

import java.time.Instant;

/**
 * @param upgraded surfaced explicitly so a rider can see they were given a bigger car for free,
 *                 rather than having to compare the two car type fields themselves
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RideResponse(String id,
                           String userId,
                           String driverId,
                           CarType requestedCarType,
                           CarType assignedCarType,
                           boolean upgraded,
                           double pickupLat,
                           double pickupLng,
                           double dropLat,
                           double dropLng,
                           RideStatus status,
                           FareResponse fare,
                           Instant startedAt,
                           Instant endedAt) {

    public static RideResponse from(RideView ride) {
        return new RideResponse(ride.id(),
                ride.userId(),
                ride.driverId(),
                ride.requestedCarType(),
                ride.assignedCarType(),
                ride.upgraded(),
                ride.pickup().lat(),
                ride.pickup().lng(),
                ride.drop().lat(),
                ride.drop().lng(),
                ride.status(),
                FareResponse.from(ride.fare()),
                ride.startedAt(),
                ride.endedAt());
    }
}
