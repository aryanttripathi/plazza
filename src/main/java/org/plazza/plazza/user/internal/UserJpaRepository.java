package org.plazza.plazza.user.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserJpaRepository extends JpaRepository<UserEntity, String> {

    Optional<UserEntity> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
