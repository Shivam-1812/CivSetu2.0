package com.civsetu.issue.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;

import com.civsetu.common.domain.Priority;
import com.civsetu.issue.entity.IssueStatus;

/**
 * Query parameters for GET /api/v1/issues and /api/v1/issues/map. All optional.
 *
 * @param status     one or more statuses, e.g. {@code status=REPORTED&status=ASSIGNED}
 * @param category   category code, e.g. {@code POTHOLE}
 * @param department department id
 * @param priority   one or more priorities
 * @param from       created at or after (ISO-8601 instant, e.g. 2026-10-01T00:00:00Z)
 * @param to         created before
 * @param q          text to find in title or description
 * @param bbox       visible map area {@code minLng,minLat,maxLng,maxLat}
 * @param overdue    true: only open issues past their deadline
 */
public record IssueSearchParams(
        List<IssueStatus> status,
        String category,
        UUID department,
        List<Priority> priority,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
        @Size(max = 100) String q,
        String bbox,
        Boolean overdue) {
}
