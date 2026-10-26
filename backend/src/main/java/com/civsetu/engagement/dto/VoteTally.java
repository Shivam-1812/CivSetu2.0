package com.civsetu.engagement.dto;

import com.civsetu.engagement.entity.VoteValue;

/**
 * Vote counts for an issue, plus the caller's own vote (null if none or not signed in).
 */
public record VoteTally(long agree, long disagree, VoteValue myVote) {

    public long net() {
        return agree - disagree;
    }
}
