package org.plazza.plazza.ride.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.ride.RideStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A ride, from booking to completion.
 *
 * <h2>Requested vs assigned car type</h2>
 * {@code requestedCarType} is what the rider asked for and what they are billed for;
 * {@code assignedCarType} is the vehicle that actually turned up. When no hatchback is free and a
 * sedan takes the trip, the two differ and the free upgrade costs zero arithmetic — it is simply
 * two columns disagreeing.
 *
 * <h2>activeUserId / activeDriverId</h2>
 * These are not business data. They mirror {@code userId} / {@code driverId} while the ride is
 * ONGOING and are set to {@code null} the moment it reaches a terminal state. Because MySQL unique
 * indexes ignore NULLs, {@code uk_active_user} and {@code uk_active_driver} then enforce "at most
 * one ongoing ride per rider and per driver" in the database — a guarantee that service-level
 * check-then-insert cannot make under concurrency.
 */
@Entity
@Table(name = "rides",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_active_user", columnNames = "active_user_id"),
           @UniqueConstraint(name = "uk_active_driver", columnNames = "active_driver_id")
       },
       indexes = {
           @Index(name = "ix_rides_user", columnList = "user_id"),
           @Index(name = "ix_rides_driver", columnList = "driver_id"),
           @Index(name = "ix_rides_status", columnList = "status")
       })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RideEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false, updatable = false)
    private String userId;

    @Column(name = "driver_id", length = 36, nullable = false, updatable = false)
    private String driverId;

    /** What the rider asked for — and what the fare is computed against. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_car_type", nullable = false, length = 20)
    private CarType requestedCarType;

    /** What actually showed up. Differs from the requested type on a free upgrade. */
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_car_type", nullable = false, length = 20)
    private CarType assignedCarType;

    @Column(name = "pickup_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal pickupLat;

    @Column(name = "pickup_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal pickupLng;

    @Column(name = "drop_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal dropLat;

    @Column(name = "drop_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal dropLng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideStatus status;

    /** Captured at booking, evaluated at ride end when the fare is known. */
    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(name = "distance_km", precision = 10, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "base_fare", precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "surge_multiplier", precision = 5, scale = 2)
    private BigDecimal surgeMultiplier;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "total_fare", precision = 10, scale = 2)
    private BigDecimal totalFare;

    @Column(name = "active_user_id", length = 36)
    private String activeUserId;

    @Column(name = "active_driver_id", length = 36)
    private String activeDriverId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public RideEntity(String userId,
                      String driverId,
                      CarType requestedCarType,
                      CarType assignedCarType,
                      Location pickup,
                      Location drop,
                      String couponCode) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.driverId = driverId;
        this.requestedCarType = requestedCarType;
        this.assignedCarType = assignedCarType;
        this.pickupLat = pickup.latAsDecimal();
        this.pickupLng = pickup.lngAsDecimal();
        this.dropLat = drop.latAsDecimal();
        this.dropLng = drop.lngAsDecimal();
        this.couponCode = couponCode;
        this.status = RideStatus.ONGOING;
        this.startedAt = Instant.now();
        this.activeUserId = userId;
        this.activeDriverId = driverId;
    }

    public Location pickup() {
        return Location.of(pickupLat, pickupLng);
    }

    public Location drop() {
        return Location.of(dropLat, dropLng);
    }

    /** True when the rider got a roomier car than they paid for. */
    public boolean isUpgraded() {
        return assignedCarType != requestedCarType;
    }

    /**
     * Moves the ride to a terminal state and clears the active-ride markers, which is what frees
     * the rider and the driver to take part in another ride.
     */
    public void close(RideStatus terminalStatus) {
        this.status = terminalStatus;
        this.endedAt = Instant.now();
        this.activeUserId = null;
        this.activeDriverId = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RideEntity that && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
