package org.plazza.plazza.common.geo;

/**
 * The seam between "how far was this ride" and "what does it cost".
 * <p>
 * The shipped implementation is straight-line haversine. A real deployment swaps in a routing
 * provider here; nothing in the pricing pipeline changes, because pricing only ever receives a
 * number of kilometres.
 */
@FunctionalInterface
public interface DistanceCalculator {

    double distanceKm(Location from, Location to);
}
