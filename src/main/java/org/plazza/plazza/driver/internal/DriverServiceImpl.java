package org.plazza.plazza.driver.internal;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.NotFoundException;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.geo.GeoUtils;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.common.text.Texts;
import org.plazza.plazza.driver.DriverService;
import org.plazza.plazza.driver.DriverStatus;
import org.plazza.plazza.driver.DriverView;
import org.plazza.plazza.driver.RegisterDriverCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
class DriverServiceImpl implements DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverServiceImpl.class);

    private static final BigDecimal MIN_RATING = BigDecimal.ZERO;
    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);
    private static final double METRES_PER_KM = 1000.0;

    private final DriverJpaRepository repository;

    DriverServiceImpl(DriverJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DriverView register(RegisterDriverCommand command) {
        String name = Texts.requireNonBlank(command.name(), "name");

        if (command.carType() == null) {
            throw new ValidationException("carType is required");
        }
        if (command.location() == null) {
            throw new ValidationException("location is required");
        }
        BigDecimal rating = requireValidRating(command.rating());

        return toView(repository.save(new DriverEntity(name, command.carType(), rating, command.location())));
    }

    @Override
    @Transactional(readOnly = true)
    public DriverView requireById(String id) {
        return toView(requireEntity(id));
    }

    @Override
    @Transactional
    public void updateLocation(String driverId, Location location) {
        if (location == null) {
            throw new ValidationException("location is required");
        }
        requireEntity(driverId).applyLocation(location);
    }

    @Override
    @Transactional
    public DriverView updateStatus(String driverId, DriverStatus status) {
        if (status == null) {
            throw new ValidationException("status is required");
        }

        DriverEntity driver = requireEntity(driverId);

        // Going offline mid-ride would strand a rider, and coming back online from ON_TRIP would
        // make the same driver bookable twice. Both are refused here rather than left to chance.
        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            throw new ValidationException(
                    "driver " + driverId + " is on a trip; end or cancel the ride before changing status");
        }
        if (status == DriverStatus.ON_TRIP) {
            throw new ValidationException("ON_TRIP is set by booking a ride, not by a status update");
        }

        driver.setStatus(status);
        return toView(driver);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverView> findAvailableWithin(Location pickup, double radiusKm, Collection<CarType> carTypes) {
        if (pickup == null) {
            throw new ValidationException("pickup location is required");
        }
        if (radiusKm <= 0) {
            throw new ValidationException("radiusKm must be positive, got " + radiusKm);
        }
        if (carTypes == null || carTypes.isEmpty()) {
            return List.of();
        }

        // Bounding box first so the query can use ix_drivers_position; ST_Distance_Sphere then
        // applies the exact circular test to the few rows that survive. The box is derived from the
        // same earth radius as the exact filter, so it can never exclude a driver truly in range.
        double latDelta = GeoUtils.latitudeDelta(radiusKm);
        double lngDelta = GeoUtils.longitudeDelta(radiusKm, pickup.lat());

        Set<String> names = carTypes.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());

        List<DriverView> found = repository.findAvailableWithin(
                        names,
                        pickup.lat(), pickup.lng(),
                        pickup.lat() - latDelta, pickup.lat() + latDelta,
                        pickup.lng() - lngDelta, pickup.lng() + lngDelta,
                        radiusKm * METRES_PER_KM)
                .stream()
                .map(DriverServiceImpl::toView)
                .toList();

        log.debug("found {} available drivers of {} within {} km of {}", found.size(), names, radiusKm, pickup);
        return found;
    }

    @Override
    @Transactional
    public boolean tryReserve(String driverId) {
        int updated = repository.tryReserve(driverId, DriverStatus.AVAILABLE, DriverStatus.ON_TRIP);

        if (updated == 0) {
            log.debug("driver {} was taken before this booking could reserve it", driverId);
        }
        return updated == 1;
    }

    @Override
    @Transactional
    public void release(String driverId) {
        repository.release(driverId, DriverStatus.ON_TRIP, DriverStatus.AVAILABLE);
    }

    private DriverEntity requireEntity(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("driver", id));
    }

    private static BigDecimal requireValidRating(BigDecimal rating) {
        if (rating == null) {
            throw new ValidationException("rating is required");
        }
        if (rating.compareTo(MIN_RATING) < 0 || rating.compareTo(MAX_RATING) > 0) {
            throw new ValidationException("rating must be between 0 and 5, got " + rating);
        }
        return rating;
    }

    private static DriverView toView(DriverEntity entity) {
        return new DriverView(entity.getId(),
                entity.getName(),
                entity.getCarType(),
                entity.getRating(),
                entity.location(),
                entity.getStatus());
    }
}
