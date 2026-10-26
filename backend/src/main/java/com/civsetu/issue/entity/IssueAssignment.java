package com.civsetu.issue.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * One period during which a department (and Authority) was responsible for an issue.
 * All assignments are made by the system.
 */
@Entity
@Table(name = "issue_assignments")
public class IssueAssignment extends BaseEntity {

    public enum Reason {
        /** First routing after the report. */
        TRIAGE,
        /** Routed again after a department returned it. */
        RE_ROUTE,
        /** Moved to another Authority of the same department (none was available, or one was disabled). */
        REBALANCE,
        /** Reopened by the reporter. */
        REOPEN
    }

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Column(name = "authority_id", updatable = false)
    private UUID authorityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Reason reason;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected IssueAssignment() {
    }

    public IssueAssignment(UUID issueId, UUID departmentId, UUID authorityId, Reason reason) {
        this.issueId = issueId;
        this.departmentId = departmentId;
        this.authorityId = authorityId;
        this.reason = reason;
    }

    public void end(Instant at) {
        this.endedAt = at;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getAuthorityId() {
        return authorityId;
    }

    public Reason getReason() {
        return reason;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
