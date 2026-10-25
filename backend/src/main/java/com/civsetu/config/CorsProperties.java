package com.civsetu.config;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Frontend origins allowed to call the API, e.g. CIVSETU_CORS_ORIGINS=https://civsetu.example.org
 * (comma-separated for several).
 */
@Validated
@ConfigurationProperties("civsetu.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
