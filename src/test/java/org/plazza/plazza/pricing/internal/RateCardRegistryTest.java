package org.plazza.plazza.pricing.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.pricing.RateCardProperties;
import org.plazza.plazza.pricing.RateCardProperties.TierConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.plazza.plazza.pricing.internal.RateCards.UNBOUNDED_KM;
import static org.plazza.plazza.pricing.internal.RateCards.bd;

/**
 * A malformed rate card does not crash — it silently misprices, and the only symptom is money.
 * These tests exist so that failure mode becomes a startup error naming the offending car type.
 */
class RateCardRegistryTest {

    private static RateCardRegistry sedanWith(List<TierConfig> tiers) {
        return RateCards.of(CarType.SEDAN, bd(50), tiers);
    }

    @Test
    @DisplayName("a gap between slabs is rejected")
    void gapIsRejected() {
        assertThatThrownBy(() -> sedanWith(List.of(
                new TierConfig(0, 2, bd(10)),
                new TierConfig(3, 5, bd(8)))))          // the 2-3 km band prices at nothing
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not contiguous")
                .hasMessageContaining("SEDAN");
    }

    @Test
    @DisplayName("overlapping slabs are rejected")
    void overlapIsRejected() {
        assertThatThrownBy(() -> sedanWith(List.of(
                new TierConfig(0, 3, bd(10)),
                new TierConfig(2, 5, bd(8)))))          // the 2-3 km band would be billed twice
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not contiguous");
    }

    @Test
    @DisplayName("a card that does not start at zero km is rejected")
    void mustStartAtZero() {
        assertThatThrownBy(() -> sedanWith(List.of(new TierConfig(1, 5, bd(10)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must start at 0 km");
    }

    @Test
    @DisplayName("an empty or inverted slab is rejected")
    void emptySlabIsRejected() {
        assertThatThrownBy(() -> sedanWith(List.of(new TierConfig(0, 0, bd(10)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty tier");
    }

    @Test
    @DisplayName("a negative rate is rejected")
    void negativeRateIsRejected() {
        assertThatThrownBy(() -> sedanWith(List.of(new TierConfig(0, 5, bd(-1)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("negative rate");
    }

    @Test
    @DisplayName("a card with no tiers is rejected")
    void emptyCardIsRejected() {
        assertThatThrownBy(() -> RateCards.of(CarType.SEDAN, bd(50), List.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no tiers");
    }

    @Test
    @DisplayName("a negative minimum fare is rejected")
    void negativeMinimumFareIsRejected() {
        assertThatThrownBy(() -> RateCards.of(CarType.SEDAN, bd(-1),
                List.of(new TierConfig(0, 5, bd(10)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("non-negative minimumFare");
    }

    @Test
    @DisplayName("configuring no cards at all is rejected")
    void noCardsIsRejected() {
        assertThatThrownBy(() -> new RateCardRegistry(new RateCardProperties(Map.of())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("no pricing.cards configured");
    }

    @Test
    @DisplayName("slabs supplied out of order are sorted rather than rejected")
    void outOfOrderTiersAreSorted() {
        RateCardRegistry registry = sedanWith(List.of(
                new TierConfig(5, UNBOUNDED_KM, bd(5)),
                new TierConfig(0, 2, bd(10)),
                new TierConfig(2, 5, bd(8))));

        // Same 7 km answer as the correctly ordered card: configuration order is not a trap.
        assertThat(registry.cardFor(CarType.SEDAN).baseFareFor(7)).isEqualByComparingTo(bd(54));
    }

    @Test
    @DisplayName("a well-formed card is accepted and exposed by car type")
    void wellFormedCardIsAccepted() {
        RateCardRegistry registry = RateCards.defaults();

        assertThatCode(() -> registry.cardFor(CarType.SEDAN)).doesNotThrowAnyException();
        assertThat(registry.hasCardFor(CarType.HATCHBACK)).isTrue();
        assertThat(registry.hasCardFor(CarType.SUV)).isFalse();
        assertThat(registry.cardFor(CarType.SEDAN).minimumFare()).isEqualByComparingTo(bd(50));
    }

    @Test
    @DisplayName("the slab total is available separately from the floored base fare")
    void slabTotalIsSeparateFromTheFloor() {
        RateCard sedan = RateCards.defaults().cardFor(CarType.SEDAN);

        assertThat(sedan.slabTotalFor(3)).isEqualByComparingTo(bd(28));   // before the floor
        assertThat(sedan.baseFareFor(3)).isEqualByComparingTo(bd(50));    // after it
    }

    @Test
    @DisplayName("adding a car type is configuration only")
    void addingACarTypeIsConfigurationOnly() {
        // The live-extension exercise: an SUV card with no code change anywhere.
        RateCardRegistry registry = RateCards.of(CarType.SUV, bd(80), List.of(
                new TierConfig(0, 2, bd(15)),
                new TierConfig(2, 5, bd(12)),
                new TierConfig(5, UNBOUNDED_KM, bd(9))));

        assertThat(registry.cardFor(CarType.SUV).baseFareFor(7))
                .isEqualByComparingTo(BigDecimal.valueOf(84));            // 30 + 36 + 18
    }
}
