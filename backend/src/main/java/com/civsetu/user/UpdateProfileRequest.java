package com.civsetu.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile edit. Omitted fields stay unchanged; email and role cannot be changed here.
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Pattern(regexp = UserService.PHONE_PATTERN, message = "must be 7 to 15 digits, optionally starting with +")
        String phone) {
}
