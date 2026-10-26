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
 * so each feature owns its own tables.
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

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Version
    private long version;

    protected Issue() {
    }

    /** A new report: status REPORTED, routed to the category's department. */
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

    public UUID getDuplicateOfId() {
        return duplicateOfId;
    }

    public long getVersion() {
        return version;
    }
}
