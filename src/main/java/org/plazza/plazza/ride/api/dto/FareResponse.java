package org.plazza.plazza.ride.api.dto;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.pricing.FareBreakdown;

import java.math.BigDecimal;

/**
 * The itemised fare. Every stage is returned rather than just the total, so a rider (or an
 * interviewer) can see exactly how the number was reached.
 */
public record FareResponse(double distanceKm,
                           CarType billedCarType,
                           BigDecimal baseFare,
                           BigDecimal surgeMultiplier,
                           BigDecimal fareAfterSurge,
                           BigDecimal discount,
                           BigDecimal total) {

    public static FareResponse from(FareBreakdown fare) {
        return fare == null ? null : new FareResponse(
                Math.round(fare.distanceKm() * 1000.0) / 1000.0,
                fare.billedCarType(),
                fare.baseFare(),
                fare.surgeMultiplier(),
                fare.fareAfterSurge(),
                fare.discount(),
                fare.total());
    }
}
