package org.plazza.plazza.coupon.internal.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.coupon.CouponType;
import org.plazza.plazza.coupon.internal.CouponEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    private static CouponEntity percent(double value, Double maxDiscount) {
        return new CouponEntity("SAVE" + (int) value, CouponType.PERCENT, bd(value),
                maxDiscount == null ? null : bd(maxDiscount), null);
    }

    private static CouponEntity flat(double value) {
        return new CouponEntity("FLAT" + (int) value, CouponType.FLAT, bd(value), null, null);
    }

    @Nested
    @DisplayName("percentage coupons")
    class Percent {

        private final PercentDiscountPolicy policy = new PercentDiscountPolicy();

        @Test
        @DisplayName("takes its percentage off the fare")
        void takesAPercentage() {
            assertThat(policy.discountFor(percent(20, null), bd(100))).isEqualByComparingTo(bd(20));
        }

        @Test
        @DisplayName("stops at maxDiscount on an expensive ride")
        void capped() {
            // 20% of 500 is 100, but the coupon promises at most 50
            assertThat(policy.discountFor(percent(20, 50.0), bd(500))).isEqualByComparingTo(bd(50));
        }

        @Test
        @DisplayName("the cap does not inflate a small discount")
        void capIsACeilingNotAFloor() {
            // 20% of 54 is 10.80, well under the 50 cap, so the cap is irrelevant here
            assertThat(policy.discountFor(percent(20, 50.0), bd(54))).isEqualByComparingTo(bd(10.80));
        }

        @Test
        @DisplayName("rounds to two decimal places")
        void rounds() {
            // 15% of 53.33 = 7.9995
            CouponEntity coupon = new CouponEntity("P15", CouponType.PERCENT, bd(15), null, null);
            assertThat(policy.discountFor(coupon, bd(53.33))).isEqualByComparingTo(bd(8.00));
        }

        @Test
        @DisplayName("a 100% coupon makes the ride free but not negative")
        void fullDiscount() {
            assertThat(policy.discountFor(percent(100, null), bd(54))).isEqualByComparingTo(bd(54));
        }

        @Test
        @DisplayName("serves only PERCENT coupons")
        void supportsOnlyPercent() {
            assertThat(policy.supports(CouponType.PERCENT)).isTrue();
            assertThat(policy.supports(CouponType.FLAT)).isFalse();
        }
    }

    @Nested
    @DisplayName("flat coupons")
    class Flat {

        private final FlatDiscountPolicy policy = new FlatDiscountPolicy();

        @Test
        @DisplayName("takes its rupee amount off the fare")
        void takesAFlatAmount() {
            assertThat(policy.discountFor(flat(30), bd(54))).isEqualByComparingTo(bd(30));
        }

        @Test
        @DisplayName("never exceeds the fare, so a big coupon makes the ride free rather than a refund")
        void neverExceedsTheFare() {
            assertThat(policy.discountFor(flat(100), bd(54))).isEqualByComparingTo(bd(54));
        }

        @Test
        @DisplayName("serves only FLAT coupons")
        void supportsOnlyFlat() {
            assertThat(policy.supports(CouponType.FLAT)).isTrue();
            assertThat(policy.supports(CouponType.PERCENT)).isFalse();
        }
    }

    @Nested
    @DisplayName("coupon validity")
    class Validity {

        @Test
        @DisplayName("a coupon with no expiry is always valid while active")
        void noExpiry() {
            assertThat(flat(30).isValidAt(NOW)).isTrue();
        }

        @Test
        @DisplayName("a future expiry is valid, a past one is not")
        void expiry() {
            CouponEntity live = new CouponEntity("LIVE", CouponType.FLAT, bd(30), null,
                    NOW.plus(1, ChronoUnit.DAYS));
            CouponEntity dead = new CouponEntity("DEAD", CouponType.FLAT, bd(30), null,
                    NOW.minus(1, ChronoUnit.DAYS));

            assertThat(live.isValidAt(NOW)).isTrue();
            assertThat(dead.isValidAt(NOW)).isFalse();
            assertThat(dead.invalidReason(NOW)).contains("expired");
        }

        @Test
        @DisplayName("expiry is exclusive: a coupon is dead at the instant it expires")
        void expiryIsExclusive() {
            CouponEntity coupon = new CouponEntity("EDGE", CouponType.FLAT, bd(30), null, NOW);
            assertThat(coupon.isValidAt(NOW)).isFalse();
        }

        @Test
        @DisplayName("a deactivated coupon is invalid and says why")
        void deactivated() {
            CouponEntity coupon = flat(30);
            coupon.setActive(false);

            assertThat(coupon.isValidAt(NOW)).isFalse();
            assertThat(coupon.invalidReason(NOW)).contains("not active");
        }

        @Test
        @DisplayName("a usable coupon has no invalid reason")
        void validCouponHasNoReason() {
            assertThat(flat(30).invalidReason(NOW)).isNull();
        }
    }
}
