package com.civsetu.issue.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The assigned Authority closes this issue because {@code duplicateOfId} already covers the same
 * problem. The original must be another issue that is still open or resolved.
 */
public record DuplicateRequest(
        @NotNull Long version,
        @NotNull UUID duplicateOfId,
        @Size(max = 1000) String remarks) {
}
