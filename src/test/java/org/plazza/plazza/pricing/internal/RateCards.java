package org.plazza.plazza.pricing.internal;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.pricing.RateCardProperties;
import org.plazza.plazza.pricing.RateCardProperties.CardConfig;
import org.plazza.plazza.pricing.RateCardProperties.TierConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Rate cards for tests, mirroring the shipped {@code application.yml}.
 * <p>
 * Built in code rather than loaded from the yaml so the pricing tests need no Spring context and
 * cannot break because someone re-tuned production pricing.
 */
final class RateCards {

    /** Effectively "and beyond" — matches the open-ended final tier in configuration. */
    static final double UNBOUNDED_KM = 100_000;

    private RateCards() {
    }

    /** SEDAN and HATCHBACK on the rates from the problem statement. */
    static RateCardRegistry defaults() {
        return new RateCardRegistry(new RateCardProperties(Map.of(
                CarType.SEDAN, new CardConfig(bd(50), List.of(
                        new TierConfig(0, 2, bd(10)),
                        new TierConfig(2, 5, bd(8)),
                        new TierConfig(5, UNBOUNDED_KM, bd(5)))),
                CarType.HATCHBACK, new CardConfig(bd(40), List.of(
                        new TierConfig(0, 2, bd(8)),
                        new TierConfig(2, 5, bd(6)),
                        new TierConfig(5, UNBOUNDED_KM, bd(4)))))));
    }

    /**
     * The sedan slabs with no minimum fare, so a test can assert slab arithmetic on short trips
     * where the floor would otherwise hide it.
     */
    static RateCardRegistry withoutMinimumFare() {
        return new RateCardRegistry(new RateCardProperties(Map.of(
                CarType.SEDAN, new CardConfig(BigDecimal.ZERO, List.of(
                        new TierConfig(0, 2, bd(10)),
                        new TierConfig(2, 5, bd(8)),
                        new TierConfig(5, UNBOUNDED_KM, bd(5)))))));
    }

    static RateCardRegistry of(CarType carType, BigDecimal minimumFare, List<TierConfig> tiers) {
        return new RateCardRegistry(
                new RateCardProperties(Map.of(carType, new CardConfig(minimumFare, tiers))));
    }

    static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
