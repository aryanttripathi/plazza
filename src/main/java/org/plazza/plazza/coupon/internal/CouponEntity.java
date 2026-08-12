package org.plazza.plazza.coupon.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.plazza.plazza.coupon.CouponType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A discount coupon, keyed by its own code.
 * <p>
 * The code is stored in the canonical form produced by {@code Texts.normalizeCode} — trimmed and
 * upper-cased — and lookups normalise the same way, so {@code " save20 "} and {@code SAVE20} resolve
 * to one row rather than two.
 */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEntity {

    @Id
    @Column(length = 40, nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type;

    /** A percentage for PERCENT coupons, a rupee amount for FLAT ones. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** Ceiling on a PERCENT discount. Null means uncapped. */
    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    /** Null means the coupon never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active;

    public CouponEntity(String code,
                        CouponType type,
                        BigDecimal value,
                        BigDecimal maxDiscount,
                        Instant expiresAt) {
        this.code = code;
        this.type = type;
        this.value = value;
        this.maxDiscount = maxDiscount;
        this.expiresAt = expiresAt;
        this.active = true;
    }

    /** Usable at the given instant: still active and not past its expiry. */
    public boolean isValidAt(Instant when) {
        return active && (expiresAt == null || expiresAt.isAfter(when));
    }

    /** Why the coupon cannot be used, or {@code null} when it can. */
    public String invalidReason(Instant when) {
        if (!active) {
            return "coupon is not active";
        }
        if (expiresAt != null && !expiresAt.isAfter(when)) {
            return "coupon expired at " + expiresAt;
        }
        return null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CouponEntity that && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
