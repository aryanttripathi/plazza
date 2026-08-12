package org.plazza.plazza.coupon.internal;

import org.plazza.plazza.common.error.InvalidCouponException;
import org.plazza.plazza.common.error.NotFoundException;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.money.Money;
import org.plazza.plazza.common.text.Texts;
import org.plazza.plazza.coupon.CouponService;
import org.plazza.plazza.coupon.CouponType;
import org.plazza.plazza.coupon.CouponView;
import org.plazza.plazza.coupon.CreateCouponCommand;
import org.plazza.plazza.coupon.internal.policy.DiscountPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
class CouponServiceImpl implements CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponServiceImpl.class);

    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

    private final CouponJpaRepository repository;
    private final List<DiscountPolicy> policies;

    CouponServiceImpl(CouponJpaRepository repository, List<DiscountPolicy> policies) {
        this.repository = repository;
        this.policies = policies;
    }

    @Override
    @Transactional
    public CouponView add(CreateCouponCommand command) {
        String code = requireCode(command.code());

        if (command.type() == null) {
            throw new ValidationException("coupon type is required");
        }
        if (repository.existsById(code)) {
            throw new ValidationException("coupon " + code + " already exists");
        }

        BigDecimal value = requirePositive(command.value(), "coupon value");
        if (command.type() == CouponType.PERCENT && value.compareTo(MAX_PERCENT) > 0) {
            throw new ValidationException("a percentage coupon cannot exceed 100, got " + value);
        }
        if (command.maxDiscount() != null) {
            requirePositive(command.maxDiscount(), "maxDiscount");
        }

        // Fail now rather than at ride end: a coupon whose type has no policy would be accepted
        // here and then blow up while a rider is waiting to be charged.
        policyFor(command.type());

        CouponEntity saved = repository.save(new CouponEntity(
                code, command.type(), Money.scaled(value), Money.scaled(command.maxDiscount()), command.expiresAt()));

        return toView(saved);
    }

    @Override
    @Transactional
    public void delete(String code) {
        String normalized = requireCode(code);
        if (!repository.existsById(normalized)) {
            throw new NotFoundException("coupon", normalized);
        }
        repository.deleteById(normalized);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponView requireByCode(String code) {
        return toView(requireEntity(requireCode(code)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponView> findAll() {
        return repository.findAll().stream().map(CouponServiceImpl::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void validate(String code) {
        String normalized = requireCode(code);
        CouponEntity coupon = repository.findById(normalized)
                .orElseThrow(() -> new InvalidCouponException(normalized, "no such coupon"));

        String reason = coupon.invalidReason(Instant.now());
        if (reason != null) {
            throw new InvalidCouponException(normalized, reason);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal discountFor(String code, BigDecimal fare) {
        String normalized = Texts.normalizeCode(code);
        if (normalized == null) {
            return Money.ZERO;
        }

        CouponEntity coupon = repository.findById(normalized).orElse(null);
        if (coupon == null || !coupon.isValidAt(Instant.now())) {
            // The coupon was valid when the ride was booked but is not now. Charging full price is
            // the lesser evil: refusing would leave the rider unable to end their ride at all.
            log.warn("coupon {} was accepted at booking but is no longer usable; charging full fare", normalized);
            return Money.ZERO;
        }

        return Money.scaled(policyFor(coupon.getType()).discountFor(coupon, fare));
    }

    private DiscountPolicy policyFor(CouponType type) {
        return policies.stream()
                .filter(policy -> policy.supports(type))
                .findFirst()
                .orElseThrow(() -> new ValidationException("no discount policy handles coupon type " + type));
    }

    private CouponEntity requireEntity(String code) {
        return repository.findById(code).orElseThrow(() -> new NotFoundException("coupon", code));
    }

    /** Codes are stored and looked up in one canonical form, so " save20 " and SAVE20 are one coupon. */
    private static String requireCode(String raw) {
        String code = Texts.normalizeCode(raw);
        if (code == null) {
            throw new ValidationException("coupon code must not be blank");
        }
        return code;
    }

    private static BigDecimal requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new ValidationException(field + " must be positive, got " + value);
        }
        return value;
    }

    private static CouponView toView(CouponEntity entity) {
        return new CouponView(entity.getCode(),
                entity.getType(),
                entity.getValue(),
                entity.getMaxDiscount(),
                entity.getExpiresAt(),
                entity.isActive());
    }
}
