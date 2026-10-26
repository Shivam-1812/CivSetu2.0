package com.civsetu.department.dto;

import java.util.UUID;

import com.civsetu.department.entity.Department;

public record DepartmentResponse(UUID id, String code, String name, String area, boolean active) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getCode(), department.getName(),
                department.getArea(), department.isActive());
    }
}
