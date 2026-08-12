package org.plazza.plazza.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotBlank(message = "phone is required")
        @Pattern(regexp = "\\d{10}", message = "phone must be 10 digits")
        String phone) {
}
