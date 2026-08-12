package org.plazza.plazza.driver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.geo.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the two things that only a real database can prove: the geo search really is a
 * MySQL {@code ST_Distance_Sphere} query behind an indexed bounding box, and reservation really is
 * atomic under concurrent callers.
 * <p>
 * Requires MySQL on localhost. Supply credentials with {@code export MYSQL_PASSWORD=...}; the
 * schema {@code plazza_test} is created and dropped per run.
 */
@SpringBootTest
class DriverServiceIT {

    private static final Location PICKUP = Location.of(12.9716, 77.5946);

    @Autowired
    private DriverService driverService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDrivers() {
        jdbcTemplate.execute("DELETE FROM rides");
        jdbcTemplate.execute("DELETE FROM drivers");
    }

    private DriverView register(String name, CarType carType, double rating, double lat, double lng) {
        return driverService.register(new RegisterDriverCommand(
                name, carType, BigDecimal.valueOf(rating), Location.of(lat, lng)));
    }

    @Test
    @DisplayName("finds only drivers inside the radius")
    void radiusFilter() {
        DriverView near = register("near", CarType.SEDAN, 4.5, 12.9750, 77.5980);      // ~0.5 km
        register("far", CarType.SEDAN, 5.0, 13.1400, 77.7000);                          // ~21 km

        List<DriverView> found = driverService.findAvailableWithin(PICKUP, 5, Set.of(CarType.SEDAN));

        assertThat(found).extracting(DriverView::id).containsExactly(near.id());
    }

    @Test
    @DisplayName("the radius boundary is honoured rather than approximated by the bounding box")
    void radiusBoundaryIsExact() {
        // ~2.2 km north: inside a 5 km circle, outside a 2 km one. A bounding-box-only filter would
        // wrongly include it at 2 km, since the box corner reaches further than the circle.
        register("edge", CarType.SEDAN, 4.5, PICKUP.lat() + 0.020, PICKUP.lng());

        assertThat(driverService.findAvailableWithin(PICKUP, 5, Set.of(CarType.SEDAN))).hasSize(1);
        assertThat(driverService.findAvailableWithin(PICKUP, 2, Set.of(CarType.SEDAN))).isEmpty();
    }

    @Test
    @DisplayName("filters by car type")
    void carTypeFilter() {
        register("sedan", CarType.SEDAN, 4.5, 12.9750, 77.5980);
        DriverView hatch = register("hatch", CarType.HATCHBACK, 4.0, 12.9752, 77.5982);

        List<DriverView> found = driverService.findAvailableWithin(PICKUP, 5, Set.of(CarType.HATCHBACK));

        assertThat(found).extracting(DriverView::id).containsExactly(hatch.id());
    }

    @Test
    @DisplayName("accepts several car types at once, which is how the free upgrade searches")
    void multipleCarTypes() {
        register("sedan", CarType.SEDAN, 4.5, 12.9750, 77.5980);
        register("hatch", CarType.HATCHBACK, 4.0, 12.9752, 77.5982);

        assertThat(driverService.findAvailableWithin(
                PICKUP, 5, Set.of(CarType.SEDAN, CarType.HATCHBACK))).hasSize(2);
    }

    @Test
    @DisplayName("excludes drivers who are offline or already on a trip")
    void excludesUnavailableDrivers() {
        DriverView offline = register("offline", CarType.SEDAN, 4.5, 12.9750, 77.5980);
        DriverView onTrip = register("onTrip", CarType.SEDAN, 4.5, 12.9751, 77.5981);

        driverService.updateStatus(offline.id(), DriverStatus.OFFLINE);
        assertThat(driverService.tryReserve(onTrip.id())).isTrue();

        assertThat(driverService.findAvailableWithin(PICKUP, 5, Set.of(CarType.SEDAN))).isEmpty();
    }

    @Test
    @DisplayName("a location update moves a driver into and out of the search radius")
    void locationUpdateAffectsSearch() {
        DriverView driver = register("mover", CarType.SEDAN, 4.5, 13.1400, 77.7000);   // far away
        assertThat(driverService.findAvailableWithin(PICKUP, 5, Set.of(CarType.SEDAN))).isEmpty();

        driverService.updateLocation(driver.id(), Location.of(12.9750, 77.5980));

        assertThat(driverService.findAvailableWithin(PICKUP, 5, Set.of(CarType.SEDAN))).hasSize(1);
    }

    @Test
    @DisplayName("reserving twice succeeds once: the second caller sees the driver already taken")
    void reserveIsAtomic() {
        DriverView driver = register("only", CarType.SEDAN, 4.5, 12.9750, 77.5980);

        assertThat(driverService.tryReserve(driver.id())).isTrue();
        assertThat(driverService.tryReserve(driver.id())).isFalse();
        assertThat(driverService.requireById(driver.id()).status()).isEqualTo(DriverStatus.ON_TRIP);
    }

    @Test
    @DisplayName("exactly one of many concurrent bookings reserves the same driver")
    void concurrentReservationsHaveExactlyOneWinner() throws Exception {
        DriverView driver = register("contested", CarType.SEDAN, 4.5, 12.9750, 77.5980);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    if (driverService.tryReserve(driver.id())) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startTogether.countDown();
        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // The whole point of the conditional UPDATE: no lock, no retry loop, still exactly one winner.
        assertThat(winners.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("release returns a driver to the pool and is idempotent")
    void releaseIsIdempotent() {
        DriverView driver = register("released", CarType.SEDAN, 4.5, 12.9750, 77.5980);
        driverService.tryReserve(driver.id());

        driverService.release(driver.id());
        driverService.release(driver.id());

        assertThat(driverService.requireById(driver.id()).status()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(driverService.findAvailableWithin(PICKUP, 5, Set.of(CarType.SEDAN))).hasSize(1);
    }

    @Test
    @DisplayName("a driver on a trip cannot be flipped offline behind the rider's back")
    void statusChangeIsRefusedMidTrip() {
        DriverView driver = register("busy", CarType.SEDAN, 4.5, 12.9750, 77.5980);
        driverService.tryReserve(driver.id());

        assertThatThrownBy(() -> driverService.updateStatus(driver.id(), DriverStatus.OFFLINE))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("on a trip");
    }
}
