package com.civsetu.sla.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.civsetu.common.domain.Priority;

/**
 * Resolution target for one priority. One row per priority, editable by Admin (Phase 7).
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Priority priority;

    @Column(name = "target_hours", nullable = false)
    private int targetHours;

    @Column(name = "at_risk_ratio", nullable = false, precision = 3, scale = 2)
    private BigDecimal atRiskRatio;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SlaPolicy() {
    }

    public Priority getPriority() {
        return priority;
    }

    public Duration getTarget() {
        return Duration.ofHours(targetHours);
    }

    public BigDecimal getAtRiskRatio() {
        return atRiskRatio;
    }
}
