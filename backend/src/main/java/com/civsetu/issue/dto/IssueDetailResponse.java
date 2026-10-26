package com.civsetu.issue.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civsetu.common.domain.Priority;
import com.civsetu.engagement.dto.VoteTally;
import com.civsetu.issue.entity.IssueStatus;
import com.civsetu.issue.entity.PrioritySource;
import com.civsetu.media.dto.MediaResponse;
import com.civsetu.sla.dto.SlaState;

/**
 * Full view of one issue. Fields depend on who asks:
 * <ul>
 *   <li>everyone: the public fields, including why the system chose the priority</li>
 *   <li>the reporter: also {@code reportedByMe = true} and {@code reporter}</li>
 *   <li>Admin, or an Authority of the issue's department: also {@code reporter},
 *       {@code assignedAuthorityId} and {@code flaggedForReview}</li>
 * </ul>
 * Fields the caller may not see are null.
 *
 * @param version        send this back with any workflow action; it detects stale views
 * @param priorityReason the latest routing decision's explanation (AI or rules)
 * @param media          attached files the caller may see (photos are public; voice notes are private)
 * @param votes          agree/disagree counts and the caller's own vote
 */
public record IssueDetailResponse(
        UUID id,
        String title,
        String description,
        String categoryCode,
        String categoryLabel,
        UUID departmentId,
        String departmentName,
        IssueStatus status,
        Priority priority,
        PrioritySource prioritySource,
        String priorityReason,
        String address,
        double latitude,
        double longitude,
        Instant createdAt,
        Instant updatedAt,
        Instant slaDueAt,
        SlaState slaState,
        Instant resolvedAt,
        Instant closedAt,
        int reopenCount,
        UUID duplicateOfId,
        long version,
        boolean reportedByMe,
        Reporter reporter,
        UUID assignedAuthorityId,
        Boolean flaggedForReview,
        List<MediaResponse> media,
        VoteTally votes,
        long commentCount,
        /* Only in the response to a new report: open issues nearby that may be the same problem */
        List<SimilarIssueResponse> possibleDuplicates) {

    /** Contact details of the person who reported the issue. Private. */
    public record Reporter(UUID id, String name, String email, String phone) {
    }
}
