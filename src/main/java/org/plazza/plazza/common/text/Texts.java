package org.plazza.plazza.common.text;

import org.apache.commons.lang3.StringUtils;
import org.plazza.plazza.common.error.ValidationException;

/**
 * The single place string handling lives. Every blank check, trim, and case fold in the codebase
 * goes through here (and through {@link StringUtils} underneath) so there is one null-safe idiom
 * rather than a scatter of {@code s == null || s.trim().isEmpty()}.
 */
public final class Texts {

    private Texts() {
    }

    /**
     * Canonical form for user-supplied codes: trimmed and upper-cased, blank becomes {@code null}.
     * Applied on both write and lookup so {@code " save20 "} and {@code SAVE20} are the same coupon.
     */
    public static String normalizeCode(String raw) {
        return StringUtils.upperCase(StringUtils.trimToNull(raw));
    }

    /** Trimmed value, or {@code null} when absent or blank — for optional inbound fields. */
    public static String trimToNull(String raw) {
        return StringUtils.trimToNull(raw);
    }

    /** Trimmed value; rejects blank input with a message naming the offending field. */
    public static String requireNonBlank(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new ValidationException(field + " must not be blank");
        }
        return StringUtils.trim(value);
    }

    public static boolean isBlank(String value) {
        return StringUtils.isBlank(value);
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        return StringUtils.equalsIgnoreCase(a, b);
    }

    /** {@code value} when it carries content, otherwise {@code fallback}. */
    public static String defaultIfBlank(String value, String fallback) {
        return StringUtils.defaultIfBlank(value, fallback);
    }
}
