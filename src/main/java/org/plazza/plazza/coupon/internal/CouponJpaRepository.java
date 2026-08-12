package org.plazza.plazza.coupon.internal;

import org.springframework.data.jpa.repository.JpaRepository;

interface CouponJpaRepository extends JpaRepository<CouponEntity, String> {
}
