package com.civsetu.auth;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Login settings. Startup fails with a clear message if the JWT secret is missing or too short.
 */
@Validated
@ConfigurationProperties("civsetu.auth")
public record AuthProperties(
        @NotBlank(message = "set CIVSETU_JWT_SECRET")
        @Size(min = 32, message = "CIVSETU_JWT_SECRET must be at least 32 characters") String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @Valid @NotNull RefreshCookie refreshCookie,
        @Valid @NotNull RateLimit rateLimit) {

    public record RefreshCookie(@NotBlank String name, @NotBlank String path, boolean secure,
            @NotBlank String sameSite) {
    }

    public record RateLimit(@Positive int loginPerMinute, @Positive int registerPerMinute,
            @Positive int refreshPerMinute) {
    }
}
