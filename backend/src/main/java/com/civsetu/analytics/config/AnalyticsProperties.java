package com.civsetu.analytics.config;

import java.time.ZoneId;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param timeZone daily trends count days in this zone (the city's local time)
 */
@Validated
@ConfigurationProperties("civsetu.analytics")
public record AnalyticsProperties(@NotNull ZoneId timeZone) {
}
