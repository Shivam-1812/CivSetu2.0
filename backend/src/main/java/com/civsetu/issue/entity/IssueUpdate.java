package com.civsetu.issue.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * One step in an issue's public history. Append-only: never updated (the database refuses it).
 */
@Entity
@Table(name = "issue_updates")
public class IssueUpdate extends BaseEntity {

    /** Who performed a step. SYSTEM steps have no actor id. */
    public enum ActorRole { SYSTEM, CITIZEN, AUTHORITY }

    public enum Action {
        REPORTED, ASSIGNED, PLANNED, RESOLVED, REJECTED, RETURNED, REOPENED, REASSIGNED, PRIORITY_RAISED,
        MARKED_DUPLICATE
    }

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 20, updatable = false)
    private ActorRole actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private IssueStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private IssueStatus toStatus;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(columnDefinition = "text", updatable = false)
    private String remarks;

    @Column(name = "action_taken", columnDefinition = "text", updatable = false)
    private String actionTaken;

    @Column(name = "staff_allocated", columnDefinition = "text", updatable = false)
    private String staffAllocated;

    @Column(name = "resources_used", columnDefinition = "text", updatable = false)
    private String resourcesUsed;

    // Insertion order, assigned by the database (bigserial); used for stable ordering
    @Column(insertable = false, updatable = false)
    private Long seq;

    protected IssueUpdate() {
    }

    public IssueUpdate(UUID issueId, UUID actorId, ActorRole actorRole, Action action, IssueStatus fromStatus,
            IssueStatus toStatus, UUID departmentId, String remarks) {
        this(issueId, actorId, actorRole, action, fromStatus, toStatus, departmentId, remarks, null, null, null);
    }

    public IssueUpdate(UUID issueId, UUID actorId, ActorRole actorRole, Action action, IssueStatus fromStatus,
            IssueStatus toStatus, UUID departmentId, String remarks, String actionTaken, String staffAllocated,
            String resourcesUsed) {
        this.issueId = issueId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.departmentId = departmentId;
        this.remarks = remarks;
        this.actionTaken = actionTaken;
        this.staffAllocated = staffAllocated;
        this.resourcesUsed = resourcesUsed;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public ActorRole getActorRole() {
        return actorRole;
    }

    public Action getAction() {
        return action;
    }

    public IssueStatus getFromStatus() {
        return fromStatus;
    }

    public IssueStatus getToStatus() {
        return toStatus;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getRemarks() {
        return remarks;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public String getStaffAllocated() {
        return staffAllocated;
    }

    public String getResourcesUsed() {
        return resourcesUsed;
    }
}
