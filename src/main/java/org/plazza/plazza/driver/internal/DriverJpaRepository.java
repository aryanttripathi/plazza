package org.plazza.plazza.driver.internal;

import org.plazza.plazza.driver.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

interface DriverJpaRepository extends JpaRepository<DriverEntity, String> {

    /**
     * Atomically reserve a driver: the availability check and the state change are one statement,
     * so two concurrent bookings for the same driver cannot both succeed.
     *
     * @return 1 when this caller won the reservation, 0 when another booking got there first
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DriverEntity d
               SET d.status = :reserved
             WHERE d.id = :id
               AND d.status = :available
            """)
    int tryReserve(@Param("id") String id,
                   @Param("available") DriverStatus available,
                   @Param("reserved") DriverStatus reserved);

    /**
     * Release a driver back into the pool once their ride ends or is cancelled. The
     * {@code ON_TRIP} predicate makes the call idempotent — a repeated release is a no-op rather
     * than a way to clobber a driver who has already started another ride.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE DriverEntity d
               SET d.status = :available
             WHERE d.id = :id
               AND d.status = :reserved
            """)
    int release(@Param("id") String id,
                @Param("reserved") DriverStatus reserved,
                @Param("available") DriverStatus available);

    /**
     * Available drivers of the given car types within {@code radiusMeters} of a point.
     * <p>
     * The {@code BETWEEN} clauses are an index-assisted bounding-box prefilter on
     * {@code ix_drivers_position}; {@code ST_Distance_Sphere} then runs the exact circular test on
     * the handful of survivors. The box is computed from the same earth radius the exact filter
     * uses, so it can never exclude a driver that is genuinely in range.
     * <p>
     * Note the argument order: {@code POINT} takes longitude first.
     */
    @Query(value = """
            SELECT * FROM drivers d
             WHERE d.status = 'AVAILABLE'
               AND d.car_type IN (:carTypes)
               AND d.lat BETWEEN :minLat AND :maxLat
               AND d.lng BETWEEN :minLng AND :maxLng
               AND ST_Distance_Sphere(POINT(d.lng, d.lat), POINT(:lng, :lat)) <= :radiusMeters
            """, nativeQuery = true)
    List<DriverEntity> findAvailableWithin(@Param("carTypes") Collection<String> carTypes,
                                           @Param("lat") double lat,
                                           @Param("lng") double lng,
                                           @Param("minLat") double minLat,
                                           @Param("maxLat") double maxLat,
                                           @Param("minLng") double minLng,
                                           @Param("maxLng") double maxLng,
                                           @Param("radiusMeters") double radiusMeters);
}
