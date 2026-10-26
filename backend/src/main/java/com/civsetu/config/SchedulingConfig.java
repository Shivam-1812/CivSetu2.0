package com.civsetu.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the background jobs (workflow safety net, SLA monitor, token cleanup). Tests switch
 * them off with {@code civsetu.scheduling.enabled=false} and call the jobs directly.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "civsetu.scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {
}
