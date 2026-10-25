package com.civsetu.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civsetu.common.validation.ValidationPatterns;

/**
 * Self-service profile edit. Omitted fields stay unchanged; email and role cannot be changed here.
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phone) {
}
