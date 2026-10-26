package com.civsetu.engagement.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * One citizen's vote on one issue (unique per issue and user in the database). Votes are written
 * with an upsert, so this entity is only read.
 */
@Entity
@Table(name = "issue_votes")
public class IssueVote extends BaseEntity {

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoteValue value;

    protected IssueVote() {
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getUserId() {
        return userId;
    }

    public VoteValue getValue() {
        return value;
    }
}
