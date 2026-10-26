package com.civsetu.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Closes the issue without fixing it. A reason is required; evidence photos are optional.
 */
public record RejectRequest(
        @NotNull Long version,
        @NotBlank @Size(min = 10, max = 2000) String reason) {
}
