package org.plazza.plazza.matching;

import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.text.Texts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Picks the configured {@link DriverMatchingStrategy}.
 * <p>
 * Spring injects every implementation on the classpath, so switching between nearest and
 * highest-rated is a property change with no edit to booking logic — the stated requirement.
 * Lookup is case-insensitive because {@code nearest}, {@code NEAREST} and {@code Nearest} are all
 * the same intent from a config file.
 * <p>
 * An unknown name fails at startup, listing what is available. Falling back to a default would mean
 * a typo in configuration silently changes how every ride is matched.
 */
@Component
public class MatchingStrategyResolver {

    private final Map<String, DriverMatchingStrategy> byName;
    private final DriverMatchingStrategy configured;

    public MatchingStrategyResolver(List<DriverMatchingStrategy> strategies,
                                    @Value("${matching.strategy}") String configuredName) {

        this.byName = index(strategies);
        this.configured = lookup(configuredName);
    }

    /** The strategy every booking uses. */
    public DriverMatchingStrategy resolve() {
        return configured;
    }

    /** Strategy by name, for tests and for a future per-request override. */
    public DriverMatchingStrategy resolve(String name) {
        return lookup(name);
    }

    public List<String> availableStrategies() {
        return List.copyOf(byName.values().stream().map(DriverMatchingStrategy::name).toList());
    }

    private static Map<String, DriverMatchingStrategy> index(List<DriverMatchingStrategy> strategies) {
        if (strategies == null || strategies.isEmpty()) {
            throw new ValidationException("no DriverMatchingStrategy implementations found");
        }

        Map<String, DriverMatchingStrategy> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (DriverMatchingStrategy strategy : strategies) {
            DriverMatchingStrategy clash = index.put(strategy.name(), strategy);
            if (clash != null) {
                throw new ValidationException("two matching strategies share the name " + strategy.name()
                        + ": " + clash.getClass().getSimpleName() + " and " + strategy.getClass().getSimpleName());
            }
        }
        // Returned as-is: copying into a LinkedHashMap here would quietly drop the
        // case-insensitive comparator and make lookup exact-match again.
        return index;
    }

    private DriverMatchingStrategy lookup(String name) {
        String requested = Texts.trimToNull(name);
        DriverMatchingStrategy strategy = requested == null ? null : byName.get(requested);

        if (strategy == null) {
            throw new ValidationException("unknown matching strategy '" + name
                    + "', available: " + availableStrategies());
        }
        return strategy;
    }
}
