package com.civsetu.issue.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Workflow settings ({@code civsetu.workflow.*}).
 *
 * @param asyncTriage       run triage in the background after a report is saved (tests run it inline)
 * @param sweepEnabled      run the safety-net job that retries stuck triage and fills missing assignments
 * @param retryTriageAfter  how long an issue may wait in REPORTED before the safety net triages it
 * @param maxReopens        how often a reporter may reopen one issue
 * @param reopenWindow      how long after resolution or rejection reopening is allowed
 */
@Validated
@ConfigurationProperties("civsetu.workflow")
public record WorkflowProperties(
        boolean asyncTriage,
        boolean sweepEnabled,
        @NotNull Duration sweepInterval,
        @NotNull Duration retryTriageAfter,
        @Positive int sweepBatchSize,
        @PositiveOrZero int maxReopens,
        @NotNull Duration reopenWindow) {
}
