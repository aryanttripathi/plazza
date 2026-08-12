package org.plazza.plazza.matching.internal;

import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverView;
import org.plazza.plazza.matching.DriverMatchingStrategy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Closest driver first — the default, because it minimises the rider's wait and the driver's
 * unpaid distance at the same time.
 * <p>
 * Ties break on rating, so two equidistant drivers are separated by something meaningful rather
 * than by whatever order the database happened to return.
 */
@Component
public class NearestDriverStrategy implements DriverMatchingStrategy {

    public static final String NAME = "nearest";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DriverView> rank(List<DriverView> candidates, Location pickup) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((DriverView driver) -> driver.distanceKmTo(pickup))
                        .thenComparing(DriverView::rating, Comparator.reverseOrder()))
                .toList();
    }
}
