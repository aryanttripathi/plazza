package org.plazza.plazza.matching.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverStatus;
import org.plazza.plazza.driver.DriverView;
import org.plazza.plazza.matching.DriverMatchingStrategy;
import org.plazza.plazza.matching.MatchingStrategyResolver;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingStrategyTest {

    private static final Location PICKUP = Location.of(12.9716, 77.5946);

    /** Roughly 0.5 km, 1.5 km and 3 km north of the pickup point. */
    private static final DriverView NEAR_LOW_RATED = driver("near", 0.0045, 3.5);
    private static final DriverView MID = driver("mid", 0.0135, 4.5);
    private static final DriverView FAR_TOP_RATED = driver("far", 0.0270, 4.9);

    private static final List<DriverView> CANDIDATES = List.of(MID, FAR_TOP_RATED, NEAR_LOW_RATED);

    private static DriverView driver(String id, double latOffset, double rating) {
        return new DriverView(id, "driver-" + id, CarType.SEDAN, BigDecimal.valueOf(rating),
                Location.of(PICKUP.lat() + latOffset, PICKUP.lng()), DriverStatus.AVAILABLE);
    }

    private static List<String> idsFrom(List<DriverView> ranked) {
        return ranked.stream().map(DriverView::id).toList();
    }

    @Nested
    @DisplayName("nearest driver")
    class Nearest {

        private final DriverMatchingStrategy strategy = new NearestDriverStrategy();

        @Test
        @DisplayName("orders by distance, closest first, ignoring rating")
        void ordersByDistance() {
            assertThat(idsFrom(strategy.rank(CANDIDATES, PICKUP)))
                    .containsExactly("near", "mid", "far");
        }

        @Test
        @DisplayName("breaks a distance tie on the better rating")
        void tieBreaksOnRating() {
            DriverView worse = driver("worse", 0.0045, 3.0);
            DriverView better = driver("better", 0.0045, 4.8);

            assertThat(idsFrom(strategy.rank(List.of(worse, better), PICKUP)))
                    .containsExactly("better", "worse");
        }

        @Test
        @DisplayName("returns every candidate, so booking can fall through on a lost race")
        void keepsAllCandidates() {
            assertThat(strategy.rank(CANDIDATES, PICKUP)).hasSameSizeAs(CANDIDATES);
        }

        @Test
        @DisplayName("handles an empty candidate list")
        void emptyList() {
            assertThat(strategy.rank(List.of(), PICKUP)).isEmpty();
        }

        @Test
        @DisplayName("does not mutate the list it was given")
        void doesNotMutateInput() {
            List<DriverView> input = List.of(MID, FAR_TOP_RATED, NEAR_LOW_RATED);
            strategy.rank(input, PICKUP);

            assertThat(idsFrom(input)).containsExactly("mid", "far", "near");
        }
    }

    @Nested
    @DisplayName("highest rated driver")
    class HighestRated {

        private final DriverMatchingStrategy strategy = new HighestRatedDriverStrategy();

        @Test
        @DisplayName("orders by rating, best first, even when that driver is furthest away")
        void ordersByRating() {
            assertThat(idsFrom(strategy.rank(CANDIDATES, PICKUP)))
                    .containsExactly("far", "mid", "near");
        }

        @Test
        @DisplayName("breaks a rating tie on the shorter distance")
        void tieBreaksOnDistance() {
            DriverView closer = driver("closer", 0.0045, 4.5);
            DriverView further = driver("further", 0.0270, 4.5);

            assertThat(idsFrom(strategy.rank(List.of(further, closer), PICKUP)))
                    .containsExactly("closer", "further");
        }

        @Test
        @DisplayName("ranks the same candidates in the opposite order to nearest-driver")
        void differsFromNearest() {
            List<String> byRating = idsFrom(strategy.rank(CANDIDATES, PICKUP));
            List<String> byDistance = idsFrom(new NearestDriverStrategy().rank(CANDIDATES, PICKUP));

            assertThat(byRating).isNotEqualTo(byDistance);
        }
    }

    @Nested
    @DisplayName("strategy selection")
    class Resolution {

        private final List<DriverMatchingStrategy> strategies =
                List.of(new NearestDriverStrategy(), new HighestRatedDriverStrategy());

        @Test
        @DisplayName("resolves the configured strategy")
        void resolvesConfigured() {
            assertThat(new MatchingStrategyResolver(strategies, "nearest").resolve())
                    .isInstanceOf(NearestDriverStrategy.class);

            assertThat(new MatchingStrategyResolver(strategies, "highestRated").resolve())
                    .isInstanceOf(HighestRatedDriverStrategy.class);
        }

        @Test
        @DisplayName("configuration names are case and whitespace insensitive")
        void lenientNames() {
            assertThat(new MatchingStrategyResolver(strategies, "  HIGHESTRATED ").resolve())
                    .isInstanceOf(HighestRatedDriverStrategy.class);
        }

        @Test
        @DisplayName("an unknown strategy name fails at startup and lists the valid ones")
        void unknownNameFailsFast() {
            // Falling back to a default would let a typo silently change how every ride is matched.
            assertThatThrownBy(() -> new MatchingStrategyResolver(strategies, "closest"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("unknown matching strategy 'closest'")
                    .hasMessageContaining("nearest");
        }

        @Test
        @DisplayName("a blank strategy name is rejected")
        void blankNameFailsFast() {
            assertThatThrownBy(() -> new MatchingStrategyResolver(strategies, "   "))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("two strategies claiming the same name fail at startup")
        void duplicateNamesFailFast() {
            assertThatThrownBy(() -> new MatchingStrategyResolver(
                    List.of(new NearestDriverStrategy(), new NearestDriverStrategy()), "nearest"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("share the name");
        }
    }
}
