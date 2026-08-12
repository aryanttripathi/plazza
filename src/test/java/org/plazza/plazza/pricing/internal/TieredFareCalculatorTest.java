package org.plazza.plazza.pricing.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.pricing.DiscountResolver;
import org.plazza.plazza.pricing.FareBreakdown;
import org.plazza.plazza.pricing.FareCalculator;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.plazza.plazza.pricing.internal.RateCards.bd;

/**
 * The graded core. No Spring, no database — these run in milliseconds and are the first thing to
 * show in the demo.
 */
class TieredFareCalculatorTest {

    private final FareCalculator calculator = new TieredFareCalculator(RateCards.defaults());

    private FareBreakdown price(double distanceKm, CarType carType) {
        return calculator.quote(distanceKm, carType);
    }

    @Nested
    @DisplayName("tiered slabs")
    class Slabs {

        @Test
        @DisplayName("a 7 km sedan ride is charged across all three slabs")
        void sedan7km() {
            // 2 km x 10 + 3 km x 8 + 2 km x 5 = 20 + 24 + 10
            assertThat(price(7, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(54));
        }

        @Test
        @DisplayName("a 4 km ride uses the first two slabs only")
        void fourKmUsesTwoSlabs() {
            // 2 km x 10 + 2 km x 8 = 36. Priced without a floor, because on the real sedan card the
            // 50 minimum would swallow this and the slab arithmetic would go untested.
            FareCalculator noFloor = new TieredFareCalculator(RateCards.withoutMinimumFare());

            assertThat(noFloor.quote(4, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(36));
            assertThat(price(4, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(50));
        }

        @Test
        @DisplayName("slab boundaries are half-open, so the boundary kilometre is charged once")
        void boundariesAreHalfOpen() {
            // Without a minimum fare in the way: 2 km is exactly 2 x 10, not 2 x 10 + 0 x 8 twice over.
            FareCalculator noFloor = new TieredFareCalculator(RateCards.withoutMinimumFare());

            assertThat(noFloor.quote(2, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(20));
            assertThat(noFloor.quote(5, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(44));
        }

        @Test
        @DisplayName("a long ride keeps accruing at the final open-ended rate")
        void longRideUsesFinalTier() {
            // 20 + 24 + 95 km x 5 = 519
            assertThat(price(100, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(519));
        }
    }

    @Nested
    @DisplayName("minimum fare")
    class MinimumFare {

        @Test
        @DisplayName("a short sedan ride is floored at the minimum rather than its slab total")
        void shortRideHitsTheFloor() {
            // slabs give 2 x 10 + 1 x 8 = 28, the floor is 50
            assertThat(price(3, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(50));
        }

        @Test
        @DisplayName("a zero-distance ride still costs the minimum fare")
        void zeroDistance() {
            assertThat(price(0, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(50));
        }

        @Test
        @DisplayName("the floor does not apply once the slabs exceed it")
        void floorStopsApplying() {
            assertThat(price(7, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(54));
        }
    }

    @Nested
    @DisplayName("car type rates")
    class CarTypeRates {

        @Test
        @DisplayName("the same trip is cheaper in a hatchback")
        void hatchbackIsCheaper() {
            // 2 x 8 + 3 x 6 + 2 x 4 = 42, against the sedan's 54
            assertThat(price(7, CarType.HATCHBACK).baseFare()).isEqualByComparingTo(bd(42));
            assertThat(price(7, CarType.HATCHBACK).baseFare())
                    .isLessThan(price(7, CarType.SEDAN).baseFare());
        }

        @Test
        @DisplayName("each car type carries its own minimum fare")
        void perCarTypeMinimum() {
            assertThat(price(1, CarType.HATCHBACK).baseFare()).isEqualByComparingTo(bd(40));
            assertThat(price(1, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(50));
        }

        @Test
        @DisplayName("the breakdown records which rate card was billed")
        void breakdownNamesTheBilledCard() {
            assertThat(price(7, CarType.HATCHBACK).billedCarType()).isEqualTo(CarType.HATCHBACK);
        }

        @Test
        @DisplayName("a car type with no configured rate card is rejected, never guessed")
        void unconfiguredCarTypeIsRejected() {
            assertThatThrownBy(() -> price(7, CarType.SUV))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("no rate card configured");
        }
    }

    @Nested
    @DisplayName("pipeline ordering")
    class Ordering {

        @Test
        @DisplayName("surge multiplies the minimum fare, not the slab total it replaced")
        void surgeAppliesAfterTheFloor() {
            // The ordering test that matters: slabs give 28, the floor lifts it to 50, and 1.5x
            // then makes 75. Applying surge before the floor would give max(50, 42) = 50.
            FareBreakdown fare = calculator.calculate(
                    3, CarType.SEDAN, bd(1.5), DiscountResolver.none());

            assertThat(fare.baseFare()).isEqualByComparingTo(bd(50));
            assertThat(fare.fareAfterSurge()).isEqualByComparingTo(bd(75));
            assertThat(fare.total()).isEqualByComparingTo(bd(75));
        }

        @Test
        @DisplayName("the coupon is applied to the post-surge fare")
        void discountAppliesAfterSurge() {
            // 54 base x 2.0 surge = 108, and 10% of that is 10.80 — not 5.40 off the pre-surge fare.
            FareBreakdown fare = calculator.calculate(
                    7, CarType.SEDAN, bd(2.0),
                    postSurge -> postSurge.multiply(bd(0.10)));

            assertThat(fare.fareAfterSurge()).isEqualByComparingTo(bd(108));
            assertThat(fare.discount()).isEqualByComparingTo(bd(10.80));
            assertThat(fare.total()).isEqualByComparingTo(bd(97.20));
        }

        @Test
        @DisplayName("with no surge and no coupon the total is just the base fare")
        void defaultsAreInert() {
            FareBreakdown fare = price(7, CarType.SEDAN);

            assertThat(fare.surgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(fare.discount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(fare.total()).isEqualByComparingTo(fare.baseFare());
        }
    }

    @Nested
    @DisplayName("discount clamping")
    class Clamping {

        @Test
        @DisplayName("a discount larger than the fare makes the ride free, never a refund")
        void discountCannotExceedTheFare() {
            FareBreakdown fare = calculator.calculate(
                    7, CarType.SEDAN, BigDecimal.ONE, postSurge -> bd(500));

            assertThat(fare.discount()).isEqualByComparingTo(bd(54));
            assertThat(fare.total()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("a misbehaving policy returning a negative discount cannot inflate the fare")
        void negativeDiscountIsIgnored() {
            FareBreakdown fare = calculator.calculate(
                    7, CarType.SEDAN, BigDecimal.ONE, postSurge -> bd(-20));

            assertThat(fare.discount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(fare.total()).isEqualByComparingTo(bd(54));
        }
    }

    @Nested
    @DisplayName("money and input handling")
    class Inputs {

        @Test
        @DisplayName("every amount is returned at two decimal places")
        void amountsAreScaledForStorage() {
            FareBreakdown fare = calculator.calculate(
                    7, CarType.SEDAN, bd(1.333), postSurge -> bd(1.005));

            assertThat(fare.baseFare().scale()).isEqualTo(2);
            assertThat(fare.fareAfterSurge().scale()).isEqualTo(2);
            assertThat(fare.discount().scale()).isEqualTo(2);
            assertThat(fare.total().scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("fractional distances are billed proportionally")
        void fractionalDistance() {
            FareCalculator noFloor = new TieredFareCalculator(RateCards.withoutMinimumFare());

            // 2 x 10 + 0.5 x 8 = 24
            assertThat(noFloor.quote(2.5, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(24));
            // 2 x 10 + 3 x 8 + 0.5 x 5 = 46.50
            assertThat(noFloor.quote(5.5, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(46.50));

            // On the real card both of those sit under the 50 floor, so the minimum fare wins.
            assertThat(price(2.5, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(50));
            assertThat(price(5.5, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(50));

            // 2 x 10 + 3 x 8 + 1.5 x 5 = 51.50 clears the floor
            assertThat(price(6.5, CarType.SEDAN).baseFare()).isEqualByComparingTo(bd(51.50));
        }

        @Test
        @DisplayName("a negative distance is rejected")
        void negativeDistanceRejected() {
            assertThatThrownBy(() -> price(-1, CarType.SEDAN))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        @DisplayName("a non-positive surge multiplier is rejected")
        void nonPositiveSurgeRejected() {
            assertThatThrownBy(() -> calculator.calculate(
                    7, CarType.SEDAN, BigDecimal.ZERO, DiscountResolver.none()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("surge multiplier must be positive");
        }
    }
}
