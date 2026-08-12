package org.plazza.plazza.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape every failing endpoint returns.
 *
 * @param code       stable machine-readable identifier, e.g. NO_DRIVER_AVAILABLE
 * @param message    human-readable explanation
 * @param fieldErrors per-field messages, present only for bean-validation failures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code,
                       String message,
                       Map<String, String> fieldErrors,
                       Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError of(String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(code, message, fieldErrors, Instant.now());
    }
}
