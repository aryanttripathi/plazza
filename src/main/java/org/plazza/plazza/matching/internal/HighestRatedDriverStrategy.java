package org.plazza.plazza.matching.internal;

import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverView;
import org.plazza.plazza.matching.DriverMatchingStrategy;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Best-rated driver first, distance breaking ties.
 * <p>
 * Selected by configuration alone ({@code matching.strategy: highestRated}); the booking code that
 * consumes the ranking is identical either way.
 */
@Component
public class HighestRatedDriverStrategy implements DriverMatchingStrategy {

    public static final String NAME = "highestRated";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DriverView> rank(List<DriverView> candidates, Location pickup) {
        return candidates.stream()
                .sorted(Comparator
                        .comparing(DriverView::rating, Comparator.reverseOrder())
                        .thenComparingDouble(driver -> driver.distanceKmTo(pickup)))
                .toList();
    }
}
