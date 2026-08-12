package org.plazza.plazza.matching;

import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverView;

import java.util.List;

/**
 * Decides the order in which candidate drivers should be offered a ride.
 *
 * <h2>Why this returns a list rather than one driver</h2>
 * Returning {@code Optional<DriverView>} would make a lost reservation race fatal: the single
 * chosen driver gets taken by a concurrent booking and the request fails even though other drivers
 * are free. Returning the full ranking lets the booking loop walk down it until a reservation
 * succeeds, which keeps every concurrency concern inside {@code RideService} and leaves strategies
 * as pure, easily tested ordering functions.
 */
public interface DriverMatchingStrategy {

    /** Configuration name that selects this strategy, e.g. {@code nearest}. */
    String name();

    /**
     * Candidates in preference order, best first.
     *
     * @param candidates available drivers already filtered to the search radius and car types
     * @param pickup     where the rider is waiting
     */
    List<DriverView> rank(List<DriverView> candidates, Location pickup);
}
