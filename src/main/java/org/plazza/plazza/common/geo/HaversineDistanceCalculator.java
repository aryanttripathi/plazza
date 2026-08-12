package org.plazza.plazza.common.geo;

import org.springframework.stereotype.Component;

/** Default {@link DistanceCalculator}: great-circle distance, no external routing service. */
@Component
public class HaversineDistanceCalculator implements DistanceCalculator {

    @Override
    public double distanceKm(Location from, Location to) {
        return GeoUtils.distanceKm(from, to);
    }
}
