package org.plazza.plazza.driver.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.geo.Location;
import org.plazza.plazza.driver.DriverStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A driver and their cab.
 * <p>
 * {@code lat} and {@code lng} are indexed because the driver search prefilters on a bounding box
 * before MySQL evaluates the exact {@code ST_Distance_Sphere} predicate.
 * <p>
 * {@code status} is deliberately never mutated through this entity in the booking path: reservation
 * goes through {@link DriverJpaRepository#tryReserve} so the check and the write are one atomic
 * statement.
 */
@Entity
@Table(name = "drivers",
       indexes = {
           @Index(name = "ix_drivers_status", columnList = "status"),
           @Index(name = "ix_drivers_position", columnList = "lat, lng")
       })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverEntity {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "car_type", nullable = false, length = 20)
    private CarType carType;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal lng;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverStatus status;

    @Column(name = "location_updated_at", nullable = false)
    private Instant locationUpdatedAt;

    public DriverEntity(String name, CarType carType, BigDecimal rating, Location location) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.carType = carType;
        this.rating = rating;
        this.status = DriverStatus.AVAILABLE;
        applyLocation(location);
    }

    public void applyLocation(Location location) {
        this.lat = location.latAsDecimal();
        this.lng = location.lngAsDecimal();
        this.locationUpdatedAt = Instant.now();
    }

    public Location location() {
        return Location.of(lat, lng);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DriverEntity that && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
