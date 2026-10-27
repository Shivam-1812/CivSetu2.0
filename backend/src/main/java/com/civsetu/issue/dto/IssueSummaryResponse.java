package com.civsetu.issue.dto;

import java.time.Instant;
import java.util.UUID;

import com.civsetu.common.domain.Priority;
import com.civsetu.engagement.entity.VoteValue;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.sla.dto.SlaState;

/**
 * One row in an issue list (a feed card). Contains only public information, plus the viewer's own
 * vote. {@code coverImageUrl} is the first report photo (null if none); {@code hasVoice} only says
 * that a voice note exists, which stays private to the reporter and staff.
 */
public record IssueSummaryResponse(
        UUID id,
        String title,
        String description,
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
        Instant slaDueAt,
        SlaState slaState,
        String coverImageUrl,
        int imageCount,
        boolean hasVoice,
        long agreeCount,
        long disagreeCount,
        long commentCount,
        VoteValue myVote) {
}
