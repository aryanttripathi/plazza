package org.plazza.plazza.driver.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of the go online / go offline call. */
public record UpdateStatusRequest(
        @NotBlank(message = "status is required")
        String status) {
}
