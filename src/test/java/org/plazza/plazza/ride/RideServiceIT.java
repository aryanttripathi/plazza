package org.plazza.plazza.ride;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.DuplicateActiveRideException;
import org.plazza.plazza.common.error.IllegalRideStateException;
import org.plazza.plazza.common.error.NoDriverAvailableException;
import org.plazza.plazza.common.error.NotFoundException;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverService;
import org.plazza.plazza.driver.DriverStatus;
import org.plazza.plazza.driver.DriverView;
import org.plazza.plazza.driver.RegisterDriverCommand;
import org.plazza.plazza.pricing.FareBreakdown;
import org.plazza.plazza.user.RegisterUserCommand;
import org.plazza.plazza.user.UserService;
import org.plazza.plazza.user.UserView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end booking behaviour against real MySQL: the free upgrade, the fare pipeline, driver
 * lifecycle, and the guarantees that only the database can enforce.
 */
@SpringBootTest
class RideServiceIT {

    /** MG Road to Koramangala, ~6 km — long enough to cross all three pricing slabs. */
    private static final Location PICKUP = Location.of(12.9716, 77.5946);
    private static final Location DROP = Location.of(12.9279, 77.6271);

    @Autowired
    private RideService rideService;

    @Autowired
    private DriverService driverService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserView rider;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("DELETE FROM rides");
        jdbcTemplate.execute("DELETE FROM drivers");
        jdbcTemplate.execute("DELETE FROM users");
        rider = userService.register(new RegisterUserCommand("Rider", randomPhone()));
    }

    private static String randomPhone() {
        return String.valueOf(9_000_000_000L + (long) (Math.random() * 999_999_999L));
    }

    private DriverView driverAt(CarType carType, double rating, double lat, double lng) {
        return driverService.register(new RegisterDriverCommand(
                "driver-" + carType, carType, BigDecimal.valueOf(rating), Location.of(lat, lng)));
    }

    private RideView book(CarType carType) {
        return rideService.book(new BookRideCommand(rider.id(), PICKUP, DROP, carType, 5.0));
    }

    @Test
    @DisplayName("books the requested car type and puts the driver on a trip")
    void happyPath() {
        DriverView driver = driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);

        RideView ride = book(CarType.SEDAN);

        assertThat(ride.driverId()).isEqualTo(driver.id());
        assertThat(ride.status()).isEqualTo(RideStatus.ONGOING);
        assertThat(ride.upgraded()).isFalse();
        assertThat(ride.fare()).isNull();
        assertThat(driverService.requireById(driver.id()).status()).isEqualTo(DriverStatus.ON_TRIP);
    }

    @Test
    @DisplayName("ending a ride prices it, records the breakdown and frees the driver")
    void endRidePricesAndReleases() {
        DriverView driver = driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        RideView ride = book(CarType.SEDAN);

        FareBreakdown fare = rideService.endRide(ride.id());

        assertThat(fare.billedCarType()).isEqualTo(CarType.SEDAN);
        assertThat(fare.distanceKm()).isBetween(5.0, 7.0);
        assertThat(fare.surgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(fare.discount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fare.total()).isEqualByComparingTo(fare.baseFare());
        // ~6 km sedan: 2x10 + 3x8 + ~1x5, comfortably clear of the 50 minimum
        assertThat(fare.total()).isGreaterThan(BigDecimal.valueOf(48));

        assertThat(rideService.requireById(ride.id()).status()).isEqualTo(RideStatus.COMPLETED);
        assertThat(driverService.requireById(driver.id()).status()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("a completed ride keeps its fare breakdown")
    void completedRideCarriesItsFare() {
        driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        RideView ride = book(CarType.SEDAN);
        rideService.endRide(ride.id());

        RideView completed = rideService.requireById(ride.id());

        assertThat(completed.fare()).isNotNull();
        assertThat(completed.fare().total()).isPositive();
        assertThat(completed.endedAt()).isNotNull();
    }

    @Test
    @DisplayName("with no hatchback free, a sedan takes the trip and the rider is billed as a hatchback")
    void freeUpgrade() {
        DriverView sedan = driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);

        RideView ride = book(CarType.HATCHBACK);

        assertThat(ride.driverId()).isEqualTo(sedan.id());
        assertThat(ride.assignedCarType()).isEqualTo(CarType.SEDAN);
        assertThat(ride.requestedCarType()).isEqualTo(CarType.HATCHBACK);
        assertThat(ride.upgraded()).isTrue();

        FareBreakdown fare = rideService.endRide(ride.id());
        assertThat(fare.billedCarType()).isEqualTo(CarType.HATCHBACK);
    }

    @Test
    @DisplayName("the upgrade genuinely costs the rider nothing")
    void upgradeIsFree() {
        driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        BigDecimal upgradedTotal = rideService.endRide(book(CarType.HATCHBACK).id()).total();

        // Same trip, this time with a hatchback actually available.
        jdbcTemplate.execute("DELETE FROM rides");
        jdbcTemplate.execute("DELETE FROM drivers");
        driverAt(CarType.HATCHBACK, 4.2, 12.9750, 77.5980);
        BigDecimal hatchbackTotal = rideService.endRide(book(CarType.HATCHBACK).id()).total();

        assertThat(upgradedTotal).isEqualByComparingTo(hatchbackTotal);
    }

    @Test
    @DisplayName("an exact match is preferred over an upgrade")
    void exactMatchWinsOverUpgrade() {
        DriverView hatchback = driverAt(CarType.HATCHBACK, 4.0, 12.9760, 77.5990);
        driverAt(CarType.SEDAN, 5.0, 12.9750, 77.5980);      // closer and better rated

        RideView ride = book(CarType.HATCHBACK);

        assertThat(ride.driverId()).isEqualTo(hatchback.id());
        assertThat(ride.upgraded()).isFalse();
    }

    @Test
    @DisplayName("the cheapest upgrade class wins, even when a pricier car is nearer")
    void cheapestUpgradeClassWins() {
        driverAt(CarType.SUV, 5.0, 12.9718, 77.5948);        // almost on top of the rider
        DriverView sedan = driverAt(CarType.SEDAN, 4.0, 12.9800, 77.6020);   // further away

        RideView ride = book(CarType.HATCHBACK);

        // A single combined query ranked by distance would have dispatched the SUV.
        assertThat(ride.driverId()).isEqualTo(sedan.id());
        assertThat(ride.assignedCarType()).isEqualTo(CarType.SEDAN);
    }

    @Test
    @DisplayName("no driver in radius is rejected and reserves nobody")
    void noDriverInRadius() {
        DriverView distant = driverAt(CarType.SEDAN, 4.8, 13.1400, 77.7000);

        assertThatThrownBy(() -> book(CarType.SEDAN))
                .isInstanceOf(NoDriverAvailableException.class);

        assertThat(driverService.requireById(distant.id()).status()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("an unknown rider fails before any driver is reserved")
    void unknownRiderReservesNobody() {
        DriverView driver = driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);

        assertThatThrownBy(() -> rideService.book(
                new BookRideCommand("no-such-user", PICKUP, DROP, CarType.SEDAN, 5.0)))
                .isInstanceOf(NotFoundException.class);

        assertThat(driverService.requireById(driver.id()).status()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    @DisplayName("a rider cannot hold two ongoing rides")
    void oneActiveRidePerRider() {
        driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        driverAt(CarType.SEDAN, 4.5, 12.9752, 77.5982);
        book(CarType.SEDAN);

        assertThatThrownBy(() -> book(CarType.SEDAN))
                .isInstanceOf(DuplicateActiveRideException.class);
    }

    @Test
    @DisplayName("a rider can book again once the previous ride has ended")
    void ridingAgainAfterCompletion() {
        driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        RideView first = book(CarType.SEDAN);
        rideService.endRide(first.id());

        RideView second = book(CarType.SEDAN);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.status()).isEqualTo(RideStatus.ONGOING);
    }

    @Test
    @DisplayName("ending a ride twice is refused rather than charging twice")
    void endingTwiceIsRefused() {
        driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        RideView ride = book(CarType.SEDAN);
        rideService.endRide(ride.id());

        assertThatThrownBy(() -> rideService.endRide(ride.id()))
                .isInstanceOf(IllegalRideStateException.class);
    }

    @Test
    @DisplayName("ending an unknown ride is a 404, not a crash")
    void endingUnknownRide() {
        assertThatThrownBy(() -> rideService.endRide("no-such-ride"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("two riders racing for the last driver produce one ride and one rejection")
    void concurrentBookingsForTheLastDriver() throws Exception {
        driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        UserView otherRider = userService.register(new RegisterUserCommand("Other", randomPhone()));

        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (String riderId : List.of(rider.id(), otherRider.id())) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    rideService.book(new BookRideCommand(riderId, PICKUP, DROP, CarType.SEDAN, 5.0));
                    booked.incrementAndGet();
                } catch (NoDriverAvailableException e) {
                    rejected.incrementAndGet();
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

        assertThat(booked.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rides WHERE status = 'ONGOING'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("a lost reservation race falls through to the next candidate rather than failing")
    void lostRaceFallsThroughToNextDriver() {
        DriverView first = driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        DriverView second = driverAt(CarType.SEDAN, 4.5, 12.9760, 77.5990);

        // Simulate losing the race for the nearest driver: someone else reserved them a moment ago.
        assertThat(driverService.tryReserve(first.id())).isTrue();

        RideView ride = book(CarType.SEDAN);

        assertThat(ride.driverId()).isEqualTo(second.id());
    }

    @Test
    @DisplayName("history filters by status for riders and drivers alike")
    void history() {
        DriverView driver = driverAt(CarType.SEDAN, 4.8, 12.9750, 77.5980);
        RideView completed = book(CarType.SEDAN);
        rideService.endRide(completed.id());
        RideView ongoing = book(CarType.SEDAN);

        assertThat(rideService.historyForUser(rider.id(), null)).hasSize(2);
        assertThat(rideService.historyForUser(rider.id(), RideStatus.ONGOING))
                .extracting(RideView::id).containsExactly(ongoing.id());
        assertThat(rideService.historyForUser(rider.id(), RideStatus.COMPLETED))
                .extracting(RideView::id).containsExactly(completed.id());

        assertThat(rideService.historyForDriver(driver.id(), null)).hasSize(2);
        assertThat(rideService.historyForDriver(driver.id(), RideStatus.COMPLETED))
                .extracting(RideView::id).containsExactly(completed.id());
    }

    @Test
    @DisplayName("history for an unknown rider is a 404 rather than an empty list")
    void historyForUnknownRider() {
        assertThatThrownBy(() -> rideService.historyForUser("no-such-user", null))
                .isInstanceOf(NotFoundException.class);
    }
}
