package com.civsetu.engagement.dto;

import java.util.UUID;

/**
 * Published inside the comment's transaction. Notifications use it to tell the reporter.
 */
public record CommentAddedEvent(UUID issueId, String issueTitle, UUID reporterId, UUID commentId, UUID authorId,
        String authorName) {
}
