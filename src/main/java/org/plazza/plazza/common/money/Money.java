package org.plazza.plazza.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rupee arithmetic helpers. Money never touches {@code double} anywhere in this codebase:
 * every fare value is a {@link BigDecimal} scaled to 2 decimal places with HALF_UP rounding,
 * matching the {@code DECIMAL(10,2)} columns it is persisted into.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = scaled(BigDecimal.ZERO);

    private Money() {
    }

    /** Normalises any amount to the canonical scale and rounding used for storage and comparison. */
    public static BigDecimal scaled(BigDecimal amount) {
        return amount == null ? null : amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String amount) {
        return scaled(new BigDecimal(amount));
    }

    public static BigDecimal of(double amount) {
        return scaled(BigDecimal.valueOf(amount));
    }

    /** Product at full precision, rounded once at the end — used for distance x rate and surge. */
    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return scaled(left.multiply(right));
    }

    /** Difference floored at zero, so a discount can never produce a negative fare. */
    public static BigDecimal subtractToZero(BigDecimal amount, BigDecimal subtrahend) {
        BigDecimal result = amount.subtract(subtrahend);
        return result.signum() < 0 ? ZERO : scaled(result);
    }

    public static BigDecimal max(BigDecimal left, BigDecimal right) {
        return scaled(left.max(right));
    }

    public static BigDecimal min(BigDecimal left, BigDecimal right) {
        return scaled(left.min(right));
    }

    /** Scale-insensitive equality — {@code 50} equals {@code 50.00}, unlike {@code equals}. */
    public static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    public static boolean isNegative(BigDecimal amount) {
        return amount != null && amount.signum() < 0;
    }
}
