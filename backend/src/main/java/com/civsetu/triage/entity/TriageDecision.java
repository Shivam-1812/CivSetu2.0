package com.civsetu.triage.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.domain.Priority;
import com.civsetu.common.persistence.BaseEntity;

/**
 * One routing decision for an issue, kept for transparency. A re-routed issue has several.
 */
@Entity
@Table(name = "triage_decisions")
public class TriageDecision extends BaseEntity {

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private TriageSource source;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "department_id", nullable = false, updatable = false)
    private UUID departmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Priority priority;

    @Column(precision = 3, scale = 2, updatable = false)
    private BigDecimal confidence;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String reason;

    @Column(name = "flagged_for_review", nullable = false, updatable = false)
    private boolean flaggedForReview;

    @Column(name = "fallback_reason", length = 200, updatable = false)
    private String fallbackReason;

    @Column(name = "latency_ms", updatable = false)
    private Integer latencyMs;

    protected TriageDecision() {
    }

    public TriageDecision(UUID issueId, TriageSource source, String model, UUID categoryId, UUID departmentId,
            Priority priority, BigDecimal confidence, String reason, boolean flaggedForReview, String fallbackReason,
            Integer latencyMs) {
        this.issueId = issueId;
        this.source = source;
        this.model = model;
        this.categoryId = categoryId;
        this.departmentId = departmentId;
        this.priority = priority;
        this.confidence = confidence;
        this.reason = reason;
        this.flaggedForReview = flaggedForReview;
        this.fallbackReason = fallbackReason;
        this.latencyMs = latencyMs;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public TriageSource getSource() {
        return source;
    }

    public String getModel() {
        return model;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public Priority getPriority() {
        return priority;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }

    public boolean isFlaggedForReview() {
        return flaggedForReview;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }
}
