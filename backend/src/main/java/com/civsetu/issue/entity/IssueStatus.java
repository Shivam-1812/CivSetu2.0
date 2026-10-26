package com.civsetu.issue.entity;

/**
 * Issue lifecycle. Transitions between statuses are enforced in Phase 5 (workflow).
 * "Escalated" is not a status: SLA breaches are tracked separately.
 */
public enum IssueStatus {
    REPORTED,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    REJECTED,
    DUPLICATE
}
