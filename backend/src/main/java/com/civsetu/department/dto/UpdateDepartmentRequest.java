package com.civsetu.department.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Null fields stay unchanged. The code cannot be changed.
 */
public record UpdateDepartmentRequest(
        @Size(max = 150) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 150) String area,
        Boolean active) {
}
