package com.civsetu.engagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Hide (or restore) an abusive or off-topic comment. A reason is required when hiding and is shown publicly.
 */
public record ModerateCommentRequest(@NotNull Boolean hidden, @Size(max = 300) String reason) {
}
