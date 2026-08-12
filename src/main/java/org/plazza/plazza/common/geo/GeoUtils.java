package org.plazza.plazza.common.geo;

/**
 * Great-circle geometry. Two jobs:
 * <ol>
 *   <li>{@link #distanceKm} — the billable distance for a ride, in Java.</li>
 *   <li>{@link #latitudeDelta} / {@link #longitudeDelta} — the bounding box that lets the driver
 *       search use the indexed {@code lat}/{@code lng} columns before MySQL runs the exact
 *       {@code ST_Distance_Sphere} filter on the survivors.</li>
 * </ol>
 * Both sides use the same earth radius, so the SQL prefilter can never exclude a driver that the
 * Java calculation would have considered in range.
 */
public final class GeoUtils {

    /** Mean earth radius in kilometres, matching MySQL's {@code ST_Distance_Sphere} default. */
    public static final double EARTH_RADIUS_KM = 6371.0;

    private static final double KM_PER_DEGREE_LATITUDE = 111.0;

    /** Guards the longitude box against division by ~0 at the poles. */
    private static final double MIN_COS_LATITUDE = 0.01;

    private GeoUtils() {
    }

    /**
     * Haversine distance between two coordinates.
     * <p>
     * This is straight-line distance, not routed road distance — a documented assumption. Swapping
     * in a routing provider means implementing {@link DistanceCalculator}, not editing the fare code.
     */
    public static double distanceKm(Location from, Location to) {
        double dLat = Math.toRadians(to.lat() - from.lat());
        double dLng = Math.toRadians(to.lng() - from.lng());

        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(from.lat()))
                * Math.cos(Math.toRadians(to.lat()))
                * Math.pow(Math.sin(dLng / 2), 2);

        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** Degrees of latitude spanning {@code radiusKm} — constant everywhere on the globe. */
    public static double latitudeDelta(double radiusKm) {
        return radiusKm / KM_PER_DEGREE_LATITUDE;
    }

    /**
     * Degrees of longitude spanning {@code radiusKm} at the given latitude. Meridians converge
     * towards the poles, so the box has to widen by {@code 1 / cos(latitude)}.
     */
    public static double longitudeDelta(double radiusKm, double atLatitude) {
        double cos = Math.max(MIN_COS_LATITUDE, Math.abs(Math.cos(Math.toRadians(atLatitude))));
        return radiusKm / (KM_PER_DEGREE_LATITUDE * cos);
    }
}
