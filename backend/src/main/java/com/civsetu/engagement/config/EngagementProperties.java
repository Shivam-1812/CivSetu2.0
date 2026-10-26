package com.civsetu.engagement.config;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Community engagement settings ({@code civsetu.engagement.*}).
 *
 * @param maxCommentsPerUser comments one person may post on one issue
 * @param escalationVotes    net "agree" votes (agree minus disagree) at which the system raises an
 *                           open issue's priority one level, once
 */
@Validated
@ConfigurationProperties("civsetu.engagement")
public record EngagementProperties(@Positive int maxCommentsPerUser, @Positive int escalationVotes) {
}
