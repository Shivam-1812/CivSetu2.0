package com.civsetu.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civsetu.common.validation.ValidationPatterns;

/**
 * Public sign-up. There is deliberately no role field: every sign-up is a Citizen.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes
        @NotBlank @Size(min = 8, max = 72) String password,
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String phone) {
}
