package com.civsetu.issue.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.locationtech.jts.geom.Point;

import com.civsetu.common.domain.Priority;
import com.civsetu.common.persistence.BaseEntity;

/**
 * A reported civic issue. Related users, categories and departments are referenced by id only,
 * so each feature owns its own tables. Status only changes through the named methods below,
 * which the workflow service calls after checking the transition rules.
 */
@Entity
@Table(name = "issues")
public class Issue extends BaseEntity {

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "assigned_authority_id")
    private UUID assignedAuthorityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status = IssueStatus.REPORTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_source", nullable = false, length = 10)
    private PrioritySource prioritySource;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount;

    // Set when the AI thinks the report may be invalid; Authorities see it, nothing is auto-rejected
    @Column(name = "flagged_for_review", nullable = false)
    private boolean flaggedForReview;

    // Set once by the SLA monitor when the breach alert is sent (cleared on reopen, which sets a new deadline)
    @Column(name = "breach_notified_at")
    private Instant breachNotifiedAt;

    // Community escalation raises the priority at most once per issue
    @Column(name = "priority_raised_by_community", nullable = false)
    private boolean priorityRaisedByCommunity;

    @Version
    private long version;

    protected Issue() {
    }

    /**
     * A new report: status REPORTED. Department, priority and deadline are provisional values from
     * the category until triage decides.
     */
    public Issue(UUID reporterId, String title, String description, UUID categoryId, UUID departmentId,
            Priority priority, PrioritySource prioritySource, Point location, String address, Instant slaDueAt) {
        this.reporterId = reporterId;
        this.title = title;
        this.description = description;
        this.categoryId = categoryId;
        this.departmentId = departmentId;
        this.priority = priority;
        this.prioritySource = prioritySource;
        this.location = location;
        this.address = address;
        this.slaDueAt = slaDueAt;
    }

    /** Triage result: where the issue goes and how urgent it is. Status becomes ASSIGNED. */
    public void route(UUID categoryId, UUID departmentId, Priority priority, PrioritySource source,
            boolean flaggedForReview, Instant slaDueAt, UUID authorityId) {
        this.categoryId = categoryId;
        this.departmentId = departmentId;
        this.priority = priority;
        this.prioritySource = source;
        this.flaggedForReview = flaggedForReview;
        this.slaDueAt = slaDueAt;
        this.assignedAuthorityId = authorityId;
        this.status = IssueStatus.ASSIGNED;
    }

    /** Automatic hand-over to another Authority of the same department. Status is unchanged. */
    public void reassign(UUID authorityId) {
        this.assignedAuthorityId = authorityId;
    }

    public void startWork() {
        this.status = IssueStatus.IN_PROGRESS;
    }

    public void resolve(Instant at) {
        this.status = IssueStatus.RESOLVED;
        this.resolvedAt = at;
        this.closedAt = at;
    }

    public void reject(Instant at) {
        this.status = IssueStatus.REJECTED;
        this.closedAt = at;
    }

    /** The department says it is not theirs: back to REPORTED for re-routing. The deadline is kept. */
    public void returnForRerouting() {
        this.status = IssueStatus.REPORTED;
        this.assignedAuthorityId = null;
    }

    /** Enough residents confirmed the problem: one level more urgent, possibly an earlier deadline. */
    public void raisePriorityByCommunity(Priority raised, Instant deadline) {
        this.priority = raised;
        this.slaDueAt = deadline;
        this.priorityRaisedByCommunity = true;
    }

    /** Already being handled under another report. Closes this one. */
    public void markDuplicate(UUID originalIssueId, Instant at) {
        this.status = IssueStatus.DUPLICATE;
        this.duplicateOfId = originalIssueId;
        this.closedAt = at;
    }

    /** The reporter is not satisfied: open again with a fresh deadline. */
    public void reopen(Instant newDeadline, UUID authorityId) {
        this.status = IssueStatus.ASSIGNED;
        this.resolvedAt = null;
        this.closedAt = null;
        this.slaDueAt = newDeadline;
        this.breachNotifiedAt = null;
        this.assignedAuthorityId = authorityId;
        this.reopenCount++;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getAssignedAuthorityId() {
        return assignedAuthorityId;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public PrioritySource getPrioritySource() {
        return prioritySource;
    }

    public double getLatitude() {
        return location.getY();
    }

    public double getLongitude() {
        return location.getX();
    }

    public String getAddress() {
        return address;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public UUID getDuplicateOfId() {
        return duplicateOfId;
    }

    public int getReopenCount() {
        return reopenCount;
    }

    public Instant getBreachNotifiedAt() {
        return breachNotifiedAt;
    }

    /** REPORTED, ASSIGNED or IN_PROGRESS: still waiting to be fixed. */
    public boolean isOpen() {
        return status == IssueStatus.REPORTED || status == IssueStatus.ASSIGNED || status == IssueStatus.IN_PROGRESS;
    }

    public boolean isPriorityRaisedByCommunity() {
        return priorityRaisedByCommunity;
    }

    public boolean isFlaggedForReview() {
        return flaggedForReview;
    }

    public long getVersion() {
        return version;
    }
}
