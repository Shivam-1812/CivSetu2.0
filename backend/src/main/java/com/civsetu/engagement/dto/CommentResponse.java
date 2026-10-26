package com.civsetu.engagement.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A public comment. When moderation hid it, {@code body} is null and {@code hiddenReason} says why,
 * so readers can see that something was removed.
 *
 * @param authorRole CITIZEN, AUTHORITY or ADMIN, so official replies are recognisable
 */
public record CommentResponse(UUID id, String authorName, String authorRole, String body, boolean hidden,
        String hiddenReason, Instant createdAt) {
}
