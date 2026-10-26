package com.civsetu.issue.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Possible-duplicate search ({@code civsetu.duplicates.*}): open issues of the same category within
 * {@code radiusMeters}, reported in the last {@code windowDays}, ranked by title similarity.
 */
@Validated
@ConfigurationProperties("civsetu.duplicates")
public record DuplicateProperties(@Positive int radiusMeters, @Positive int windowDays, @Positive int maxResults) {
}
