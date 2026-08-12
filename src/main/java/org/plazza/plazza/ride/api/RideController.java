package org.plazza.plazza.ride.api;

import jakarta.validation.Valid;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.text.Texts;
import org.plazza.plazza.ride.BookRideCommand;
import org.plazza.plazza.ride.RideService;
import org.plazza.plazza.ride.RideStatus;
import org.plazza.plazza.ride.api.dto.BookRideRequest;
import org.plazza.plazza.pricing.api.dto.FareResponse;
import org.plazza.plazza.ride.api.dto.RideResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping("/rides")
    public ResponseEntity<RideResponse> book(@Valid @RequestBody BookRideRequest request) {
        RideResponse body = RideResponse.from(rideService.book(new BookRideCommand(
                request.userId(),
                request.pickup().toLocation(),
                request.drop().toLocation(),
                parseCarType(request.carType()),
                request.radiusKm(),
                request.couponCode())));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/rides/{id}/end")
    public FareResponse end(@PathVariable String id) {
        return FareResponse.from(rideService.endRide(id));
    }

    @GetMapping("/rides/{id}")
    public RideResponse get(@PathVariable String id) {
        return RideResponse.from(rideService.requireById(id));
    }

    @GetMapping("/users/{userId}/rides")
    public List<RideResponse> historyForUser(@PathVariable String userId,
                                             @RequestParam(required = false) String status) {
        return rideService.historyForUser(userId, parseStatus(status)).stream()
                .map(RideResponse::from)
                .toList();
    }

    @GetMapping("/drivers/{driverId}/rides")
    public List<RideResponse> historyForDriver(@PathVariable String driverId,
                                               @RequestParam(required = false) String status) {
        return rideService.historyForDriver(driverId, parseStatus(status)).stream()
                .map(RideResponse::from)
                .toList();
    }

    private static CarType parseCarType(String raw) {
        CarType carType = CarType.parseOrNull(raw);
        if (carType == null) {
            throw new ValidationException("unknown carType '" + raw + "', expected one of "
                    + Arrays.toString(CarType.values()));
        }
        return carType;
    }

    /** An absent filter means "all rides"; a present but unrecognised one is a mistake worth reporting. */
    private static RideStatus parseStatus(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        RideStatus status = RideStatus.parseOrNull(raw);
        if (status == null) {
            throw new ValidationException("unknown status '" + raw + "', expected one of "
                    + Arrays.toString(RideStatus.values()));
        }
        return status;
    }
}
