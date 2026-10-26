package com.civsetu.issue.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.IssueUpdate;
import com.civsetu.media.dto.MediaResponse;

/**
 * One public step in an issue's history. People are shown by role and department, not by name.
 *
 * @param media files attached to this step that the caller may see
 */
public record HistoryEntryResponse(
        UUID id,
        IssueUpdate.Action action,
        IssueStatus fromStatus,
        IssueStatus toStatus,
        IssueUpdate.ActorRole actor,
        String departmentName,
        String remarks,
        String actionTaken,
        String staffAllocated,
        String resourcesUsed,
        Instant createdAt,
        List<MediaResponse> media) {
}
