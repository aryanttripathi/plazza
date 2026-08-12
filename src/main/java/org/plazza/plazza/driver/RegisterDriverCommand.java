package org.plazza.plazza.driver;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.geo.Location;

import java.math.BigDecimal;

/** Everything needed to put a driver on the road. */
public record RegisterDriverCommand(String name, CarType carType, BigDecimal rating, Location location) {
}
