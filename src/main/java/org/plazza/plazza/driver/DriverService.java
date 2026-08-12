package org.plazza.plazza.driver;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.geo.Location;

import java.util.Collection;
import java.util.List;

/**
 * The driver module's public surface: registration, location and status upkeep, the geo search
 * that feeds matching, and the atomic reservation that makes booking safe under concurrency.
 * <p>
 * Note what is absent: nothing here decides <em>which</em> driver gets a ride. That is
 * {@code matching}'s job, and keeping the two apart is what lets the matching rule change by
 * configuration alone.
 */
public interface DriverService {

    DriverView register(RegisterDriverCommand command);

    DriverView requireById(String id);

    void updateLocation(String driverId, Location location);

    DriverView updateStatus(String driverId, DriverStatus status);

    /**
     * Available drivers of the given car types whose current position is within {@code radiusKm}
     * of the pickup point. Ordering is not meaningful — ranking belongs to the matching strategy.
     */
    List<DriverView> findAvailableWithin(Location pickup, double radiusKm, Collection<CarType> carTypes);

    /**
     * Attempts to take a driver out of the available pool.
     * <p>
     * The availability check and the state change are a single conditional UPDATE, so two riders
     * racing for the last driver cannot both win. Callers treat {@code false} as "try the next
     * candidate", not as an error.
     *
     * @return true when this caller reserved the driver
     */
    boolean tryReserve(String driverId);

    /** Returns a driver to the available pool. Idempotent: releasing a free driver does nothing. */
    void release(String driverId);
}
