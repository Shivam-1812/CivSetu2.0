package com.civsetu.issue.dto;

import java.time.Instant;
import java.util.UUID;

import com.civsetu.issue.entity.IssueStatus;

/**
 * Published whenever an issue's status changes (after the change is saved, listeners should use
 * {@code @TransactionalEventListener}). Notifications subscribe to it in Phase 6.
 *
 * @param fromStatus null when the issue was just reported
 */
public record IssueStatusChangedEvent(UUID issueId, UUID reporterId, IssueStatus fromStatus, IssueStatus toStatus,
        UUID departmentId, UUID assignedAuthorityId, Instant occurredAt) {
}
