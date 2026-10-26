package com.civsetu.notification.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Live notification stream settings ({@code civsetu.notifications.*}).
 *
 * @param heartbeat         interval of keep-alive comments, so proxies do not close idle streams
 * @param streamTimeout     a stream is closed after this long; browsers reconnect automatically
 * @param ticketTtl         how long a stream ticket can be used
 * @param maxStreamsPerUser open tabs per user; the oldest stream is closed beyond this
 */
@Validated
@ConfigurationProperties("civsetu.notifications")
public record NotificationProperties(@NotNull Duration heartbeat, @NotNull Duration streamTimeout,
        @NotNull Duration ticketTtl, @Positive int maxStreamsPerUser) {
}
