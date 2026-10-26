package com.civsetu.department.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civsetu.common.domain.Priority;
import com.civsetu.common.validation.ValidationPatterns;

public record CreateCategoryRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = ValidationPatterns.CODE_MESSAGE) String code,
        @NotBlank @Size(max = 100) String label,
        @NotNull UUID departmentId,
        @NotNull Priority defaultPriority) {
}
