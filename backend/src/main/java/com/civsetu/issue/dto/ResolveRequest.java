package com.civsetu.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Marks the issue fixed. At least one proof photo must be attached as a {@code proofs} part.
 */
public record ResolveRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 2000) String remarks) {
}
