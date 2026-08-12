package org.plazza.plazza.pricing.internal;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.pricing.RateCardProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the configured rate cards into validated, ready-to-use price lists.
 *
 * <h2>Why the validation is worth the code</h2>
 * A rate card with a gap ({@code [0,2)} then {@code [3,5)}) or an overlap ({@code [0,2)} then
 * {@code [1,5)}) does not fail — it silently misprices every ride that crosses the seam, and the
 * only symptom is money. Validating on construction converts that into a startup failure with a
 * message naming the offending car type, which is the difference between a five-second fix and an
 * afternoon of confusion.
 * <p>
 * Deliberately a plain constructor rather than {@code @PostConstruct}, so the pricing tests can
 * build a registry directly with no Spring context.
 */
@Component
public class RateCardRegistry {

    private final Map<CarType, RateCard> cards;

    public RateCardRegistry(RateCardProperties properties) {
        this.cards = build(properties);
    }

    /**
     * @throws ValidationException when no card is configured for the car type — a booking must never
     *                             fall back to a guessed price
     */
    public RateCard cardFor(CarType carType) {
        RateCard card = cards.get(carType);
        if (card == null) {
            throw new ValidationException("no rate card configured for car type " + carType);
        }
        return card;
    }

    public boolean hasCardFor(CarType carType) {
        return cards.containsKey(carType);
    }

    public List<CarType> configuredCarTypes() {
        return List.copyOf(cards.keySet());
    }

    private static Map<CarType, RateCard> build(RateCardProperties properties) {
        if (properties == null || properties.cards() == null || properties.cards().isEmpty()) {
            throw new ValidationException("no pricing.cards configured");
        }

        Map<CarType, RateCard> built = new EnumMap<>(CarType.class);
        properties.cards().forEach((carType, config) ->
                built.put(carType, toValidatedCard(carType, config)));
        return Map.copyOf(built);
    }

    private static RateCard toValidatedCard(CarType carType, RateCardProperties.CardConfig config) {
        if (config == null || config.tiers() == null || config.tiers().isEmpty()) {
            throw new ValidationException("rate card for " + carType + " has no tiers");
        }
        if (config.minimumFare() == null || config.minimumFare().signum() < 0) {
            throw new ValidationException("rate card for " + carType + " needs a non-negative minimumFare");
        }

        List<FareTier> tiers = new ArrayList<>(config.tiers().stream()
                .map(tier -> new FareTier(tier.fromKm(), tier.toKm(), tier.ratePerKm()))
                .toList());
        tiers.sort(Comparator.comparingDouble(FareTier::fromKm));

        validateContiguous(carType, tiers);
        return new RateCard(carType, config.minimumFare(), List.copyOf(tiers));
    }

    private static void validateContiguous(CarType carType, List<FareTier> tiers) {
        if (tiers.get(0).fromKm() != 0.0) {
            throw new ValidationException(
                    "rate card for " + carType + " must start at 0 km, starts at " + tiers.get(0).fromKm());
        }

        for (int i = 0; i < tiers.size(); i++) {
            FareTier tier = tiers.get(i);

            if (tier.toKm() <= tier.fromKm()) {
                throw new ValidationException(
                        "rate card for " + carType + " has an empty tier [" + tier.fromKm() + ", " + tier.toKm() + ")");
            }
            if (tier.ratePerKm() == null || tier.ratePerKm().signum() < 0) {
                throw new ValidationException(
                        "rate card for " + carType + " has a negative rate in tier starting at " + tier.fromKm());
            }
            if (i > 0 && tiers.get(i - 1).toKm() != tier.fromKm()) {
                throw new ValidationException(
                        "rate card for " + carType + " is not contiguous: tier ending at "
                                + tiers.get(i - 1).toKm() + " is followed by one starting at " + tier.fromKm());
            }
        }
    }
}
