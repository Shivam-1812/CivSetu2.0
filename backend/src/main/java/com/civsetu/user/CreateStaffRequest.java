package com.civsetu.user;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin-only: creates an AUTHORITY (with a department) or another ADMIN.
 */
public record CreateStaffRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Pattern(regexp = UserService.PHONE_PATTERN, message = "must be 7 to 15 digits, optionally starting with +")
        String phone,
        @NotNull Role role,
        UUID departmentId) {
}
