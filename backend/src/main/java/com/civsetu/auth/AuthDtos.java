package com.civsetu.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civsetu.user.UserResponse;

/**
 * Request and response bodies for /api/v1/auth.
 */
final class AuthDtos {

    private AuthDtos() {
    }

    /** Public sign-up. There is deliberately no role field: every sign-up is a Citizen. */
    record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            // BCrypt only uses the first 72 bytes
            @NotBlank @Size(min = 8, max = 72) String password,
            @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7 to 15 digits, optionally starting with +")
            String phone) {
    }

    record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    /** Access token for the Authorization header; the refresh token is set as a cookie instead. */
    record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
