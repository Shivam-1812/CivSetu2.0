package com.civsetu.engagement.dto;

import com.civsetu.engagement.entity.VoteValue;

/**
 * Community activity on one issue for a list row: vote and comment counts, and the viewer's own
 * vote (null if none or not signed in).
 */
public record EngagementSummary(long agree, long disagree, long comments, VoteValue myVote) {

    public static final EngagementSummary NONE = new EngagementSummary(0, 0, 0, null);
}
