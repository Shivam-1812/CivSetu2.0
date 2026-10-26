package com.civsetu.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The reporter is not satisfied with a resolution or rejection. Allowed a limited number of times
 * within a window after closing; the issue gets a fresh deadline.
 */
public record ReopenRequest(
        @NotNull Long version,
        @NotBlank @Size(min = 10, max = 1000) String reason) {
}
