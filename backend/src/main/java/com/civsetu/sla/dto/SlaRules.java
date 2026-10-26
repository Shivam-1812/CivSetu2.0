package com.civsetu.sla.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.civsetu.common.domain.Priority;

/**
 * The SLA targets, loaded once so many issues can be evaluated without a query each. Pure
 * calculation: "now" is passed in, so the result never depends on hidden state.
 */
public record SlaRules(Map<Priority, Duration> targets, Map<Priority, BigDecimal> atRiskRatios) {

    /**
     * @param resolvedAt null if not resolved
     * @param open       true for REPORTED, ASSIGNED and IN_PROGRESS
     * @return the state, or null for issues closed without a fix (rejected, duplicate)
     */
    public SlaState state(Priority priority, Instant dueAt, Instant resolvedAt, boolean open, Instant now) {
        if (!open) {
            if (resolvedAt == null) {
                return null;
            }
            return resolvedAt.isAfter(dueAt) ? SlaState.MISSED : SlaState.MET;
        }
        if (!now.isBefore(dueAt)) {
            return SlaState.BREACHED;
        }
        // "At risk" covers the last part of the time the priority allows (by default the second half)
        Duration target = targets.get(priority);
        double atRiskShare = 1 - atRiskRatios.get(priority).doubleValue();
        Duration atRiskWindow = Duration.ofSeconds((long) (target.toSeconds() * atRiskShare));
        return Duration.between(now, dueAt).compareTo(atRiskWindow) <= 0 ? SlaState.AT_RISK : SlaState.ON_TRACK;
    }
}
