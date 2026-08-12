package org.plazza.plazza.ride.internal;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.DuplicateActiveRideException;
import org.plazza.plazza.common.error.IllegalRideStateException;
import org.plazza.plazza.common.error.NoDriverAvailableException;
import org.plazza.plazza.common.error.NotFoundException;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.geo.DistanceCalculator;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverService;
import org.plazza.plazza.driver.DriverView;
import org.plazza.plazza.matching.MatchingStrategyResolver;
import org.plazza.plazza.pricing.DiscountResolver;
import org.plazza.plazza.pricing.FareBreakdown;
import org.plazza.plazza.pricing.FareCalculator;
import org.plazza.plazza.pricing.surge.SurgeStrategy;
import org.plazza.plazza.ride.BookRideCommand;
import org.plazza.plazza.ride.BookingProperties;
import org.plazza.plazza.ride.RideService;
import org.plazza.plazza.ride.RideStatus;
import org.plazza.plazza.ride.RideView;
import org.plazza.plazza.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
class RideServiceImpl implements RideService {

    private static final Logger log = LoggerFactory.getLogger(RideServiceImpl.class);

    private final RideJpaRepository rides;
    private final UserService users;
    private final DriverService drivers;
    private final MatchingStrategyResolver matching;
    private final FareCalculator fareCalculator;
    private final SurgeStrategy surgeStrategy;
    private final DistanceCalculator distanceCalculator;
    private final BookingProperties bookingProperties;

    RideServiceImpl(RideJpaRepository rides,
                    UserService users,
                    DriverService drivers,
                    MatchingStrategyResolver matching,
                    FareCalculator fareCalculator,
                    SurgeStrategy surgeStrategy,
                    DistanceCalculator distanceCalculator,
                    BookingProperties bookingProperties) {
        this.rides = rides;
        this.users = users;
        this.drivers = drivers;
        this.matching = matching;
        this.fareCalculator = fareCalculator;
        this.surgeStrategy = surgeStrategy;
        this.distanceCalculator = distanceCalculator;
        this.bookingProperties = bookingProperties;
    }

    @Override
    @Transactional
    public RideView book(BookRideCommand command) {
        Location pickup = require(command.pickup(), "pickup");
        Location drop = require(command.drop(), "drop");
        CarType requested = require(command.carType(), "carType");
        double radiusKm = resolveRadius(command.radiusKm());

        // Fail on an unknown rider before reserving anybody, so a bad request cannot take a driver
        // out of the pool even briefly.
        users.requireById(command.userId());

        rides.findByActiveUserId(command.userId()).ifPresent(active -> {
            throw new DuplicateActiveRideException("user", command.userId());
        });

        Candidates candidates = findCandidates(pickup, radiusKm, requested);
        DriverView reserved = reserveFirstAvailable(candidates.drivers(), pickup, radiusKm);

        RideEntity ride = rides.save(new RideEntity(
                command.userId(),
                reserved.id(),
                requested,                  // billed
                reserved.carType(),         // actually assigned
                pickup,
                drop,
                null));

        if (ride.isUpgraded()) {
            log.info("ride {} upgraded {} -> {} at no extra cost", ride.getId(), requested, reserved.carType());
        }
        return toView(ride);
    }

    @Override
    @Transactional
    public FareBreakdown endRide(String rideId) {
        // Row lock: two concurrent end requests serialise, and the second sees COMPLETED rather
        // than pricing and charging the same ride twice.
        RideEntity ride = rides.findByIdForUpdate(rideId)
                .orElseThrow(() -> new NotFoundException("ride", rideId));

        if (ride.getStatus() != RideStatus.ONGOING) {
            throw new IllegalRideStateException(rideId, ride.getStatus(), "ended");
        }

        double distanceKm = distanceCalculator.distanceKm(ride.pickup(), ride.drop());
        BigDecimal surge = surgeStrategy.multiplier(ride.pickup(), Instant.now());

        // Billed against the requested car type: this is the whole of the free-upgrade rule.
        FareBreakdown fare = fareCalculator.calculate(
                distanceKm, ride.getRequestedCarType(), surge, DiscountResolver.none());

        applyFare(ride, fare);
        ride.close(RideStatus.COMPLETED);

        // Same transaction as the ride update: a ride can never complete while its driver stays
        // stuck ON_TRIP, nor a driver be freed while the ride is still open.
        drivers.release(ride.getDriverId());

        log.info("ride {} completed: {} km, total {}", rideId, distanceKm, fare.total());
        return fare;
    }

    @Override
    @Transactional(readOnly = true)
    public RideView requireById(String rideId) {
        return rides.findById(rideId)
                .map(RideServiceImpl::toView)
                .orElseThrow(() -> new NotFoundException("ride", rideId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RideView> historyForUser(String userId, RideStatus status) {
        users.requireById(userId);

        List<RideEntity> found = status == null
                ? rides.findByUserIdOrderByStartedAtDesc(userId)
                : rides.findByUserIdAndStatusOrderByStartedAtDesc(userId, status);

        return found.stream().map(RideServiceImpl::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RideView> historyForDriver(String driverId, RideStatus status) {
        drivers.requireById(driverId);

        List<RideEntity> found = status == null
                ? rides.findByDriverIdOrderByStartedAtDesc(driverId)
                : rides.findByDriverIdAndStatusOrderByStartedAtDesc(driverId, status);

        return found.stream().map(RideServiceImpl::toView).toList();
    }

    /**
     * Drivers of the requested car type, or the cheapest upgrade class that has anyone free.
     * <p>
     * Upgrade classes are tried one rank at a time rather than in a single combined query, so a
     * rider who asked for a hatchback gets a sedan when one exists and only sees an SUV when no
     * sedan is free. A combined query ranked by distance could hand out the most expensive car in
     * the fleet while a cheaper one waited a hundred metres further away.
     */
    private Candidates findCandidates(Location pickup, double radiusKm, CarType requested) {
        List<DriverView> exact = drivers.findAvailableWithin(pickup, radiusKm, Set.of(requested));
        if (!exact.isEmpty()) {
            return new Candidates(exact, false);
        }

        for (CarType upgrade : requested.upgradesAbove()) {
            List<DriverView> found = drivers.findAvailableWithin(pickup, radiusKm, Set.of(upgrade));
            if (!found.isEmpty()) {
                return new Candidates(found, true);
            }
        }

        throw new NoDriverAvailableException(radiusKm);
    }

    /**
     * Walks the ranking until a reservation sticks.
     * <p>
     * This loop is the entire concurrency story on the booking side: {@code tryReserve} is a
     * conditional UPDATE, so losing the race costs one round trip and the next candidate is tried.
     * Only an exhausted list is a failure.
     */
    private DriverView reserveFirstAvailable(List<DriverView> candidates, Location pickup, double radiusKm) {
        for (DriverView candidate : matching.resolve().rank(candidates, pickup)) {
            if (drivers.tryReserve(candidate.id())) {
                return candidate;
            }
        }
        throw new NoDriverAvailableException(radiusKm);
    }

    private double resolveRadius(Double requested) {
        if (requested == null) {
            return bookingProperties.defaultRadiusKm();
        }
        if (requested <= 0) {
            throw new ValidationException("radiusKm must be positive, got " + requested);
        }
        return requested;
    }

    private static void applyFare(RideEntity ride, FareBreakdown fare) {
        ride.setDistanceKm(BigDecimal.valueOf(fare.distanceKm()).setScale(3, java.math.RoundingMode.HALF_UP));
        ride.setBaseFare(fare.baseFare());
        ride.setSurgeMultiplier(fare.surgeMultiplier());
        ride.setDiscount(fare.discount());
        ride.setTotalFare(fare.total());
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new ValidationException(field + " is required");
        }
        return value;
    }

    private static RideView toView(RideEntity ride) {
        return new RideView(ride.getId(),
                ride.getUserId(),
                ride.getDriverId(),
                ride.getRequestedCarType(),
                ride.getAssignedCarType(),
                ride.isUpgraded(),
                ride.pickup(),
                ride.drop(),
                ride.getStatus(),
                fareOf(ride),
                ride.getStartedAt(),
                ride.getEndedAt());
    }

    /** An ongoing ride has no fare yet; only a priced one carries a breakdown. */
    private static FareBreakdown fareOf(RideEntity ride) {
        if (ride.getTotalFare() == null) {
            return null;
        }
        return new FareBreakdown(
                ride.getDistanceKm() == null ? 0 : ride.getDistanceKm().doubleValue(),
                ride.getRequestedCarType(),
                ride.getBaseFare(),
                ride.getSurgeMultiplier(),
                ride.getBaseFare().multiply(ride.getSurgeMultiplier()).setScale(2, java.math.RoundingMode.HALF_UP),
                ride.getDiscount(),
                ride.getTotalFare());
    }

    /** Candidate drivers plus whether taking them means upgrading the rider for free. */
    private record Candidates(List<DriverView> drivers, boolean upgraded) {
    }
}
