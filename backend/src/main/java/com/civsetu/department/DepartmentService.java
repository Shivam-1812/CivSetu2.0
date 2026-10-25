package com.civsetu.department;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public entry point to the department module for other packages.
 * Admin CRUD and category routing arrive in Phase 3.
 */
@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departments;

    DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
    }

    public boolean existsActive(UUID id) {
        return id != null && departments.existsByIdAndActiveTrue(id);
    }
}
