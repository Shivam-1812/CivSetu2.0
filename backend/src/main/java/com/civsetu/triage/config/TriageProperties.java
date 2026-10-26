package com.civsetu.triage.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Automatic triage settings ({@code civsetu.triage.*}). The AI advisor is used only when an
 * API key is configured; otherwise the rule engine decides alone.
 *
 * @param timeout            longest wait for the AI before falling back to the rules
 * @param minConfidence      AI answers below this confidence (0-1) are not used
 * @param maxImages          report photos sent to the AI
 * @param fallbackDepartment department code used when no other department fits
 */
@Validated
@ConfigurationProperties("civsetu.triage")
public record TriageProperties(
        @NotNull Duration timeout,
        @DecimalMin("0.0") @DecimalMax("1.0") double minConfidence,
        @PositiveOrZero int maxImages,
        @NotBlank String fallbackDepartment,
        @Valid @NotNull Gemini gemini) {

    /** Google Gemini settings; leave the key empty to run on rules only. */
    public record Gemini(String apiKey, @NotBlank String model, @NotBlank String baseUrl) {
    }
}
