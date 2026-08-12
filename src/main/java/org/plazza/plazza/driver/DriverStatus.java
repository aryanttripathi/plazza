package org.plazza.plazza.driver;

/**
 * Driver availability.
 * <p>
 * Transitions happen only through conditional {@code UPDATE ... WHERE status = ?} statements — never
 * a read followed by a write — which is what makes it impossible for two concurrent bookings to
 * reserve the same driver.
 */
public enum DriverStatus {

    /** Online and reservable. */
    AVAILABLE,

    /** Reserved for an ongoing ride. */
    ON_TRIP,

    /** Online status withdrawn by the driver; never a matching candidate. */
    OFFLINE
}
