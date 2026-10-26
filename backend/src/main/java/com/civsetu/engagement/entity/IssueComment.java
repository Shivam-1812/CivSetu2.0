package com.civsetu.engagement.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * A public comment on an issue. Moderation hides the text but keeps the comment (and who hid it).
 */
@Entity
@Table(name = "issue_comments")
public class IssueComment extends BaseEntity {

    @Column(name = "issue_id", nullable = false, updatable = false)
    private UUID issueId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 1000, updatable = false)
    private String body;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "hidden_by")
    private UUID hiddenBy;

    @Column(name = "hidden_reason", length = 300)
    private String hiddenReason;

    // Insertion order, assigned by the database (bigserial); used for stable ordering
    @Column(insertable = false, updatable = false)
    private Long seq;

    protected IssueComment() {
    }

    public IssueComment(UUID issueId, UUID userId, String body) {
        this.issueId = issueId;
        this.userId = userId;
        this.body = body;
    }

    public void hide(UUID moderator, String reason) {
        this.hidden = true;
        this.hiddenBy = moderator;
        this.hiddenReason = reason;
    }

    public void restore() {
        this.hidden = false;
        this.hiddenBy = null;
        this.hiddenReason = null;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getBody() {
        return body;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String getHiddenReason() {
        return hiddenReason;
    }
}
