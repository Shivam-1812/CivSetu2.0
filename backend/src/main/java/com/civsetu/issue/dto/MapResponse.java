package com.civsetu.issue.dto;

import java.util.List;
import java.util.UUID;

import com.civsetu.common.domain.Priority;
import com.civsetu.issue.entity.IssueStatus;

/**
 * Issues inside a map viewport, newest first, in a slim form for markers.
 * {@code truncated} is true when more issues matched than were returned; zoom in to see them.
 */
public record MapResponse(List<Point> items, boolean truncated) {

    public record Point(UUID id, double latitude, double longitude, String categoryCode, IssueStatus status,
            Priority priority) {
    }
}
