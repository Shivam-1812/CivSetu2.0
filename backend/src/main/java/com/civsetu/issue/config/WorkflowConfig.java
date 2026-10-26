package com.civsetu.issue.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Where triage runs: a small bounded thread pool in normal use, or inline (in the request thread,
 * right after commit) when {@code civsetu.workflow.async-triage=false}, which keeps tests deterministic.
 */
@Configuration(proxyBeanMethods = false)
class WorkflowConfig {

    @Bean
    TaskExecutor triageExecutor(WorkflowProperties properties) {
        if (!properties.asyncTriage()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("triage-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // When the queue is full, new work waits for the safety-net sweep instead of failing the report
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        // Spring initialises and shuts down the pool (it is an InitializingBean / DisposableBean)
        return executor;
    }

    /** Turns on @Scheduled jobs (the workflow safety net) unless disabled. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "civsetu.workflow.sweep-enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingOn {
    }
}
