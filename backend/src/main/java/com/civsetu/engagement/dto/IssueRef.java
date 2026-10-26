package com.civsetu.engagement.dto;

import java.util.UUID;

/**
 * The few issue facts engagement needs, provided by the issue feature through {@code IssueLookup}.
 */
public record IssueRef(UUID id, UUID reporterId, String title) {
}
