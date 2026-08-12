package org.plazza.plazza.ride;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param defaultRadiusKm how far to search when a booking does not specify a radius
 */
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(double defaultRadiusKm) {
}
