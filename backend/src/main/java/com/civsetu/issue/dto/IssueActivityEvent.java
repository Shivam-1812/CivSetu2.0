package com.civsetu.issue.dto;

import java.time.Instant;
import java.util.UUID;

import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;

/**
 * Published inside the transaction for every step added to an issue's history (status changes,
 * hand-overs, priority raises). Listeners that store data should use
 * {@code @TransactionalEventListener(phase = BEFORE_COMMIT)} so they commit together with the step.
 *
 * @param actorId    who performed the step; null for the system
 * @param fromStatus null when the issue was just reported
 */
public record IssueActivityEvent(UUID issueId, String issueTitle, IssueUpdate.Action action, IssueStatus fromStatus,
        IssueStatus toStatus, UUID reporterId, UUID departmentId, UUID assignedAuthorityId, UUID actorId,
        String remarks, Instant occurredAt) {
}
