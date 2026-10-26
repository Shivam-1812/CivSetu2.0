package com.civsetu.department.dto;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civsetu.common.domain.Priority;

/**
 * Null fields stay unchanged. The code cannot be changed. Changing the department only
 * affects issues reported afterwards.
 */
public record UpdateCategoryRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String label,
        UUID departmentId,
        Priority defaultPriority,
        Boolean active) {
}
