package com.civsetu.issue.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new report. The reporter comes from the access token, and the department, status,
 * priority and deadline are set by the server, so none of them are accepted here.
 */
public record CreateIssueRequest(
        @NotBlank @Size(min = 5, max = 150) String title,
        @NotBlank @Size(min = 10, max = 2000) String description,
        /* Category code from GET /api/v1/categories, e.g. POTHOLE */
        @NotBlank String category,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotBlank @Size(max = 300) String address) {
}
