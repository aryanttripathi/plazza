package org.plazza.plazza.pricing;

import org.plazza.plazza.common.enums.CarType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Pricing as configuration rather than code.
 * <p>
 * Adding a car type or changing a slab is an edit to {@code application.yml} — there is no
 * {@code if (carType == SEDAN)} anywhere in this codebase to go looking for. Bound from:
 * <pre>
 * pricing:
 *   cards:
 *     SEDAN:
 *       minimumFare: 50
 *       tiers:
 *         - { fromKm: 0, toKm: 2, ratePerKm: 10 }
 * </pre>
 */
@ConfigurationProperties(prefix = "pricing")
public record RateCardProperties(Map<CarType, CardConfig> cards) {

    /**
     * @param minimumFare floor applied to the slab total before surge and before any coupon
     * @param tiers       ascending, contiguous, half-open {@code [fromKm, toKm)} slabs
     */
    public record CardConfig(BigDecimal minimumFare, List<TierConfig> tiers) {
    }

    /**
     * One pricing slab. {@code toKm} is exclusive, so slabs meeting at a boundary charge that
     * kilometre exactly once.
     */
    public record TierConfig(double fromKm, double toKm, BigDecimal ratePerKm) {
    }
}
