package com.civsetu.issue.dto;

import java.time.Instant;
import java.util.UUID;

import com.civsetu.common.domain.Priority;
import com.civsetu.issue.entity.IssueStatus;

/**
 * One row in an issue list. Contains only public information.
 */
public record IssueSummaryResponse(
        UUID id,
        String title,
        String categoryCode,
        String categoryLabel,
        UUID departmentId,
        String departmentName,
        IssueStatus status,
        Priority priority,
        String address,
        double latitude,
        double longitude,
        Instant createdAt,
        Instant slaDueAt) {
}
