package com.civsetu.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civsetu.common.validation.ValidationPatterns;

public record CreateDepartmentRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.CODE, message = ValidationPatterns.CODE_MESSAGE) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 150) String area) {
}
