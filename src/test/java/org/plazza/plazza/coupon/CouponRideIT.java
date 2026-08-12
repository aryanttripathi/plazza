package org.plazza.plazza.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.InvalidCouponException;
import org.plazza.plazza.common.error.NotFoundException;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverService;
import org.plazza.plazza.driver.DriverStatus;
import org.plazza.plazza.driver.RegisterDriverCommand;
import org.plazza.plazza.pricing.FareBreakdown;
import org.plazza.plazza.ride.BookRideCommand;
import org.plazza.plazza.ride.RideService;
import org.plazza.plazza.ride.RideView;
import org.plazza.plazza.user.RegisterUserCommand;
import org.plazza.plazza.user.UserService;
import org.plazza.plazza.user.UserView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coupons end to end: CRUD, validation at booking, and the discount actually landing on the fare.
 */
@SpringBootTest
class CouponRideIT {

    private static final Location PICKUP = Location.of(12.9716, 77.5946);
    private static final Location DROP = Location.of(12.9279, 77.6271);

    @Autowired
    private CouponService couponService;

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
        jdbcTemplate.execute("DELETE FROM coupons");
        rider = userService.register(new RegisterUserCommand(
                "Rider", String.valueOf(9_000_000_000L + (long) (Math.random() * 999_999_999L))));
    }

    private void aSedanNearby() {
        driverService.register(new RegisterDriverCommand(
                "Ravi", CarType.SEDAN, BigDecimal.valueOf(4.8), Location.of(12.9750, 77.5980)));
    }

    private CouponView percent(String code, double value, Double cap) {
        return couponService.add(new CreateCouponCommand(code, CouponType.PERCENT,
                BigDecimal.valueOf(value), cap == null ? null : BigDecimal.valueOf(cap), null));
    }

    private CouponView flat(String code, double value) {
        return couponService.add(new CreateCouponCommand(
                code, CouponType.FLAT, BigDecimal.valueOf(value), null, null));
    }

    private FareBreakdown rideWith(String couponCode) {
        RideView ride = rideService.book(new BookRideCommand(
                rider.id(), PICKUP, DROP, CarType.SEDAN, 5.0, couponCode));
        return rideService.endRide(ride.id());
    }

    @Test
    @DisplayName("a flat coupon comes off the fare")
    void flatCouponApplied() {
        aSedanNearby();
        flat("FLAT30", 30);

        FareBreakdown fare = rideWith("FLAT30");

        // The fixture ride is a 50.00 minimum-fare trip.
        assertThat(fare.discount()).isEqualByComparingTo(BigDecimal.valueOf(30));
        assertThat(fare.total()).isEqualByComparingTo(fare.fareAfterSurge().subtract(fare.discount()));
    }

    @Test
    @DisplayName("a percentage coupon comes off the fare")
    void percentCouponApplied() {
        aSedanNearby();
        percent("SAVE20", 20, null);

        FareBreakdown fare = rideWith("SAVE20");

        BigDecimal expected = fare.fareAfterSurge()
                .multiply(BigDecimal.valueOf(0.20))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        assertThat(fare.discount()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("a percentage coupon stops at its cap")
    void percentCouponCapped() {
        aSedanNearby();
        percent("SAVE20", 20, 5.0);

        assertThat(rideWith("SAVE20").discount()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    @DisplayName("a coupon larger than the fare makes the ride free rather than a refund")
    void oversizedCouponFloorsAtZero() {
        aSedanNearby();
        flat("HUGE", 500);

        FareBreakdown fare = rideWith("HUGE");

        assertThat(fare.discount()).isEqualByComparingTo(fare.fareAfterSurge());
        assertThat(fare.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("coupon codes ignore surrounding whitespace and case")
    void codesAreNormalised() {
        aSedanNearby();
        flat("SAVE20", 20);

        assertThat(rideWith("  save20  ").discount()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    @DisplayName("a coupon stored in lower case is found by its upper case name")
    void storedCodesAreNormalisedToo() {
        couponService.add(new CreateCouponCommand(
                " flat15 ", CouponType.FLAT, BigDecimal.valueOf(15), null, null));

        assertThat(couponService.requireByCode("FLAT15").code()).isEqualTo("FLAT15");
        assertThatCode(() -> couponService.validate("flat15")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unknown coupon is rejected at booking, before a driver is reserved")
    void unknownCouponRejectedAtBooking() {
        aSedanNearby();

        assertThatThrownBy(() -> rideService.book(new BookRideCommand(
                rider.id(), PICKUP, DROP, CarType.SEDAN, 5.0, "NOPE")))
                .isInstanceOf(InvalidCouponException.class);

        // The whole point of validating first: nobody was taken out of the pool.
        assertThat(driverService.findAvailableWithin(PICKUP, 5, java.util.Set.of(CarType.SEDAN)))
                .hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rides", Integer.class)).isZero();
    }

    @Test
    @DisplayName("an expired coupon is rejected at booking")
    void expiredCouponRejected() {
        aSedanNearby();
        couponService.add(new CreateCouponCommand("OLD10", CouponType.FLAT, BigDecimal.TEN, null,
                Instant.now().minus(1, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> rideService.book(new BookRideCommand(
                rider.id(), PICKUP, DROP, CarType.SEDAN, 5.0, "OLD10")))
                .isInstanceOf(InvalidCouponException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("a coupon deleted mid-ride charges full fare rather than blocking the ride")
    void couponDeletedMidRide() {
        aSedanNearby();
        flat("FLAT30", 30);

        RideView ride = rideService.book(new BookRideCommand(
                rider.id(), PICKUP, DROP, CarType.SEDAN, 5.0, "FLAT30"));
        couponService.delete("FLAT30");

        // Refusing here would strand the rider in an ongoing ride they cannot end.
        FareBreakdown fare = rideService.endRide(ride.id());

        assertThat(fare.discount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fare.total()).isEqualByComparingTo(fare.fareAfterSurge());
    }

    @Test
    @DisplayName("a ride without a coupon is undiscounted")
    void noCoupon() {
        aSedanNearby();

        FareBreakdown fare = rideWith(null);

        assertThat(fare.discount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fare.total()).isEqualByComparingTo(fare.baseFare());
    }

    @Test
    @DisplayName("the driver is released normally on a discounted ride")
    void discountedRideStillReleasesDriver() {
        aSedanNearby();
        flat("FLAT30", 30);
        rideWith("FLAT30");

        assertThat(driverService.findAvailableWithin(PICKUP, 5, java.util.Set.of(CarType.SEDAN)))
                .singleElement()
                .satisfies(driver -> assertThat(driver.status()).isEqualTo(DriverStatus.AVAILABLE));
    }

    @Test
    @DisplayName("adding a duplicate coupon code is rejected")
    void duplicateCodeRejected() {
        flat("SAVE20", 20);

        assertThatThrownBy(() -> flat("save20", 25))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("a percentage above 100 is rejected")
    void percentAbove100Rejected() {
        assertThatThrownBy(() -> percent("TOOMUCH", 120, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot exceed 100");
    }

    @Test
    @DisplayName("a non-positive coupon value is rejected")
    void nonPositiveValueRejected() {
        assertThatThrownBy(() -> flat("ZERO", 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    @DisplayName("deleting an unknown coupon is a 404")
    void deleteUnknown() {
        assertThatThrownBy(() -> couponService.delete("GHOST"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a deleted coupon is gone and no longer validates")
    void deletedCouponIsGone() {
        flat("TEMP10", 10);
        couponService.delete("temp10");

        assertThat(couponService.findAll()).isEmpty();
        assertThatThrownBy(() -> couponService.validate("TEMP10"))
                .isInstanceOf(InvalidCouponException.class);
    }
}
