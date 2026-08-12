package org.plazza.plazza.driver.api.dto;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.driver.DriverStatus;
import org.plazza.plazza.driver.DriverView;

import java.math.BigDecimal;

public record DriverResponse(String id,
                             String name,
                             CarType carType,
                             BigDecimal rating,
                             double lat,
                             double lng,
                             DriverStatus status) {

    public static DriverResponse from(DriverView view) {
        return new DriverResponse(view.id(),
                view.name(),
                view.carType(),
                view.rating(),
                view.location().lat(),
                view.location().lng(),
                view.status());
    }
}
