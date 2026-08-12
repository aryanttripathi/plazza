package org.plazza.plazza.pricing;

import org.plazza.plazza.pricing.surge.SurgeStrategy;
import org.plazza.plazza.pricing.surge.internal.NoSurgeStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for the pricing module. */
@Configuration
@EnableConfigurationProperties(RateCardProperties.class)
public class PricingConfig {

    /**
     * Surge is off unless something else provides a strategy.
     * <p>
     * {@code @ConditionalOnMissingBean} belongs on a {@code @Bean} method rather than on a
     * {@code @Component} class, where it would depend on bean-definition ordering and quietly
     * misbehave.
     */
    @Bean
    @ConditionalOnMissingBean(SurgeStrategy.class)
    public SurgeStrategy noSurgeStrategy() {
        return new NoSurgeStrategy();
    }
}
