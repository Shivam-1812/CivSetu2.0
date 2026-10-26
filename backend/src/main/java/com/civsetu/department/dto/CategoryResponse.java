package com.civsetu.department.dto;

import java.util.UUID;

import com.civsetu.common.domain.Priority;
import com.civsetu.department.entity.Category;

public record CategoryResponse(UUID id, String code, String label, UUID departmentId, Priority defaultPriority,
        boolean active) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getLabel(),
                category.getDefaultDepartmentId(), category.getDefaultPriority(), category.isActive());
    }
}
