package org.plazza.plazza.driver.api;

import jakarta.validation.Valid;
import org.plazza.plazza.common.api.dto.LocationRequest;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.driver.DriverService;
import org.plazza.plazza.driver.DriverStatus;
import org.plazza.plazza.driver.RegisterDriverCommand;
import org.plazza.plazza.driver.api.dto.DriverResponse;
import org.plazza.plazza.driver.api.dto.RegisterDriverRequest;
import org.plazza.plazza.driver.api.dto.UpdateStatusRequest;
import org.plazza.plazza.common.geo.Location;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin edge: validate, translate to a command, delegate. No business rules live here — the same
 * decisions have to hold for any caller, not just an HTTP one.
 */
@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    public ResponseEntity<DriverResponse> register(@Valid @RequestBody RegisterDriverRequest request) {
        CarType carType = parseCarType(request.carType());

        DriverResponse body = DriverResponse.from(driverService.register(new RegisterDriverCommand(
                request.name(),
                carType,
                request.rating(),
                Location.of(request.lat(), request.lng()))));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable String id) {
        return DriverResponse.from(driverService.requireById(id));
    }

    /** Cab location ping. Frequent and small, so it is a PATCH rather than a full update. */
    @PatchMapping("/{id}/location")
    public DriverResponse updateLocation(@PathVariable String id,
                                         @Valid @RequestBody LocationRequest request) {
        driverService.updateLocation(id, request.toLocation());
        return DriverResponse.from(driverService.requireById(id));
    }

    @PatchMapping("/{id}/status")
    public DriverResponse updateStatus(@PathVariable String id,
                                       @Valid @RequestBody UpdateStatusRequest request) {
        DriverStatus status = DriverStatus.parseOrNull(request.status());
        if (status == null) {
            throw new ValidationException("unknown status '" + request.status()
                    + "', expected one of AVAILABLE, OFFLINE");
        }
        return DriverResponse.from(driverService.updateStatus(id, status));
    }

    private static CarType parseCarType(String raw) {
        CarType carType = CarType.parseOrNull(raw);
        if (carType == null) {
            throw new ValidationException("unknown carType '" + raw + "', expected one of "
                    + java.util.Arrays.toString(CarType.values()));
        }
        return carType;
    }
}
