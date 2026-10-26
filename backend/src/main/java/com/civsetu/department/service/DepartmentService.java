package com.civsetu.department.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.audit.service.AuditService;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.department.dto.CreateDepartmentRequest;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.dto.UpdateDepartmentRequest;
import com.civsetu.department.entity.Department;
import com.civsetu.department.repository.CategoryRepository;
import com.civsetu.department.repository.DepartmentRepository;

/**
 * Public entry point to departments: who handles which issues.
 */
@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private static final Sort BY_NAME = Sort.by("name");

    private final DepartmentRepository departments;
    private final CategoryRepository categories;
    private final AuditService audit;

    DepartmentService(DepartmentRepository departments, CategoryRepository categories, AuditService audit) {
        this.departments = departments;
        this.categories = categories;
        this.audit = audit;
    }

    public boolean existsActive(UUID id) {
        return id != null && departments.existsByIdAndActiveTrue(id);
    }

    public List<DepartmentResponse> listActive() {
        return departments.findByActiveTrue(BY_NAME).stream().map(DepartmentResponse::from).toList();
    }

    public List<DepartmentResponse> listAll() {
        return departments.findAll(BY_NAME).stream().map(DepartmentResponse::from).toList();
    }

    /** Every department by id, for labelling lists of issues. The table is small. */
    public Map<UUID, DepartmentResponse> lookup() {
        return departments.findAll().stream()
                .map(DepartmentResponse::from)
                .collect(Collectors.toMap(DepartmentResponse::id, Function.identity()));
    }

    @Transactional
    public DepartmentResponse create(Caller admin, CreateDepartmentRequest request) {
        if (departments.existsByCode(request.code())) {
            throw codeTaken();
        }
        try {
            Department saved = departments.saveAndFlush(
                    new Department(request.code(), request.name().trim(), trimToNull(request.area())));
            audit.record(admin, "DEPARTMENT_CREATED", "DEPARTMENT", saved.getId(), "code=" + saved.getCode());
            return DepartmentResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw codeTaken();
        }
    }

    @Transactional
    public DepartmentResponse update(Caller admin, UUID id, UpdateDepartmentRequest request) {
        Department department = departments.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
        if (request.name() != null) {
            department.setName(request.name().trim());
        }
        if (request.area() != null) {
            department.setArea(trimToNull(request.area()));
        }
        if (Boolean.FALSE.equals(request.active()) && categories.existsByDefaultDepartmentIdAndActiveTrue(id)) {
            // Otherwise new issues would be routed to an inactive department
            throw ApiException.conflict("DEPARTMENT_IN_USE",
                    "Move or deactivate the categories routed to this department first.");
        }
        if (request.active() != null) {
            department.setActive(request.active());
        }
        audit.record(admin, "DEPARTMENT_UPDATED", "DEPARTMENT", id, describe(request));
        return DepartmentResponse.from(department);
    }

    private static ApiException codeTaken() {
        return ApiException.conflict("CODE_TAKEN", "A department with this code already exists.");
    }

    private static String describe(UpdateDepartmentRequest request) {
        return "name=" + request.name() + ", area=" + request.area() + ", active=" + request.active();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
