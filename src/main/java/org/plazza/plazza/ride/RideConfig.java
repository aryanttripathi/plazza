package org.plazza.plazza.ride;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring for the ride module. */
@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class RideConfig {
}
