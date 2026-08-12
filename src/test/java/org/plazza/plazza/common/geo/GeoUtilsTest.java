package org.plazza.plazza.common.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.common.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoUtilsTest {

    // MG Road and Koramangala, Bengaluru — the fixture used throughout the demo.
    private static final Location MG_ROAD = Location.of(12.9716, 77.5946);
    private static final Location KORAMANGALA = Location.of(12.9279, 77.6271);

    @Test
    @DisplayName("distance between two known points is right to within a few hundred metres")
    void knownDistance() {
        assertThat(GeoUtils.distanceKm(MG_ROAD, KORAMANGALA)).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    @DisplayName("distance from a point to itself is zero")
    void zeroDistance() {
        assertThat(GeoUtils.distanceKm(MG_ROAD, MG_ROAD)).isZero();
    }

    @Test
    @DisplayName("distance is symmetric")
    void symmetric() {
        assertThat(GeoUtils.distanceKm(MG_ROAD, KORAMANGALA))
                .isEqualTo(GeoUtils.distanceKm(KORAMANGALA, MG_ROAD));
    }

    @Test
    @DisplayName("one degree of latitude is about 111 km anywhere on the globe")
    void oneDegreeOfLatitude() {
        assertThat(GeoUtils.distanceKm(Location.of(0, 0), Location.of(1, 0)))
                .isCloseTo(111.19, org.assertj.core.data.Offset.offset(0.2));
    }

    @Test
    @DisplayName("the bounding box is never narrower than the radius it has to cover")
    void boundingBoxCoversTheRadius() {
        // The box prefilters an indexed query, so being too small would silently drop drivers that
        // the exact ST_Distance_Sphere test would have accepted.
        double radiusKm = 5;

        double latSpanKm = GeoUtils.distanceKm(
                Location.of(12.9716, 77.5946),
                Location.of(12.9716 + GeoUtils.latitudeDelta(radiusKm), 77.5946));

        double lngSpanKm = GeoUtils.distanceKm(
                Location.of(12.9716, 77.5946),
                Location.of(12.9716, 77.5946 + GeoUtils.longitudeDelta(radiusKm, 12.9716)));

        assertThat(latSpanKm).isGreaterThanOrEqualTo(radiusKm);
        assertThat(lngSpanKm).isGreaterThanOrEqualTo(radiusKm);
    }

    @Test
    @DisplayName("the longitude box widens towards the poles")
    void longitudeBoxWidensWithLatitude() {
        assertThat(GeoUtils.longitudeDelta(5, 60)).isGreaterThan(GeoUtils.longitudeDelta(5, 0));
    }

    @Test
    @DisplayName("an out-of-range coordinate is rejected at construction")
    void coordinatesAreValidated() {
        assertThatThrownBy(() -> Location.of(91, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("latitude");

        assertThatThrownBy(() -> Location.of(0, 181))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("longitude");
    }
}
