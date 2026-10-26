package com.civsetu.analytics.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * An open issue past its deadline.
 */
public record OverdueIssue(UUID id, String title, String departmentName, String priority, Instant slaDueAt,
        double hoursOverdue) {
}
