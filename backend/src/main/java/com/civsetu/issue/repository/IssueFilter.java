package com.civsetu.issue.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civsetu.common.domain.Priority;
import com.civsetu.issue.entity.IssueStatus;

/**
 * Resolved search filters. Every field is optional; null or empty means "no restriction".
 * Bounding box coordinates are WGS 84 degrees. {@code overdueAt} keeps only open issues whose
 * deadline was before that instant.
 */
public record IssueFilter(
        List<IssueStatus> statuses,
        UUID categoryId,
        UUID departmentId,
        List<Priority> priorities,
        Instant createdFrom,
        Instant createdTo,
        String text,
        UUID reporterId,
        UUID assignedAuthorityId,
        Double minLongitude,
        Double minLatitude,
        Double maxLongitude,
        Double maxLatitude,
        Instant overdueAt) {

    public boolean hasBoundingBox() {
        return minLongitude != null && minLatitude != null && maxLongitude != null && maxLatitude != null;
    }
}
