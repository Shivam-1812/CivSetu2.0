package com.civsetu.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

/**
 * An in-app message for one user, usually about an issue they reported or handle.
 */
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(nullable = false, length = 150, updatable = false)
    private String title;

    @Column(nullable = false, length = 1000, updatable = false)
    private String body;

    @Column(name = "issue_id", updatable = false)
    private UUID issueId;

    @Column(name = "read_at")
    private Instant readAt;

    // Insertion order, assigned by the database (bigserial); used for stable ordering
    @Column(insertable = false, updatable = false)
    private Long seq;

    protected Notification() {
    }

    public Notification(UUID userId, NotificationType type, String title, String body, UUID issueId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.issueId = issueId;
    }

    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getIssueId() {
        return issueId;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
