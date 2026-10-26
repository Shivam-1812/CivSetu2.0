package com.civsetu.engagement.dto;

import java.util.UUID;

/**
 * Published inside the vote's transaction whenever an issue's vote counts change. The issue feature
 * uses it for community escalation.
 */
public record VoteTallyChangedEvent(UUID issueId, long agree, long disagree) {

    public long net() {
        return agree - disagree;
    }
}
