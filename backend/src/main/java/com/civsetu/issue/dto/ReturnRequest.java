package com.civsetu.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The assigned Authority says the issue belongs to another department. The system re-routes it,
 * never back to a department that already returned it. The deadline keeps running.
 */
public record ReturnRequest(
        @NotNull Long version,
        @NotBlank @Size(min = 10, max = 1000) String reason) {
}
