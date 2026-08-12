package org.plazza.plazza.ride.internal;

import jakarta.persistence.LockModeType;
import org.plazza.plazza.ride.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface RideJpaRepository extends JpaRepository<RideEntity, String> {

    /**
     * Loads a ride under a row lock for state transitions (end, cancel), so two concurrent requests
     * to end the same ride serialise and the second one sees COMPLETED rather than double-charging.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RideEntity r WHERE r.id = :id")
    Optional<RideEntity> findByIdForUpdate(@Param("id") String id);

    List<RideEntity> findByUserIdOrderByStartedAtDesc(String userId);

    List<RideEntity> findByUserIdAndStatusOrderByStartedAtDesc(String userId, RideStatus status);

    List<RideEntity> findByDriverIdOrderByStartedAtDesc(String driverId);

    List<RideEntity> findByDriverIdAndStatusOrderByStartedAtDesc(String driverId, RideStatus status);

    Optional<RideEntity> findByActiveUserId(String userId);

    Optional<RideEntity> findByActiveDriverId(String driverId);
}
