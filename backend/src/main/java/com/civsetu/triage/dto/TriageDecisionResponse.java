package com.civsetu.triage.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.civsetu.common.domain.Priority;
import com.civsetu.triage.entity.TriageDecision;
import com.civsetu.triage.entity.TriageSource;

/**
 * A routing decision: where the issue goes, how urgent it is, and why.
 */
public record TriageDecisionResponse(
        UUID id,
        TriageSource source,
        UUID categoryId,
        UUID departmentId,
        Priority priority,
        BigDecimal confidence,
        String reason,
        boolean flaggedForReview,
        Instant decidedAt) {

    public static TriageDecisionResponse from(TriageDecision decision) {
        return new TriageDecisionResponse(decision.getId(), decision.getSource(), decision.getCategoryId(),
                decision.getDepartmentId(), decision.getPriority(), decision.getConfidence(), decision.getReason(),
                decision.isFlaggedForReview(), decision.getCreatedAt());
    }
}
