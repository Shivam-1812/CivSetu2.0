package com.civsetu.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The assigned Authority's work plan. Moves the issue to IN_PROGRESS.
 *
 * @param version the issue version the Authority saw; a different current version means someone
 *                else changed it first (409 STALE_ISSUE)
 */
public record PlanRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 2000) String actionTaken,
        @Size(max = 500) String staffAllocated,
        @Size(max = 500) String resourcesUsed,
        @Size(max = 2000) String remarks) {
}
