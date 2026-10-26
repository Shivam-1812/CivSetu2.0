package com.civsetu.department.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.audit.service.AuditService;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.Caller;
import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.dto.CreateCategoryRequest;
import com.civsetu.department.dto.UpdateCategoryRequest;
import com.civsetu.department.entity.Category;
import com.civsetu.department.repository.CategoryRepository;
import com.civsetu.department.repository.DepartmentRepository;

/**
 * Issue categories and their routing: category → default department and priority.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Sort BY_LABEL = Sort.by("label");

    private final CategoryRepository categories;
    private final DepartmentRepository departments;
    private final AuditService audit;

    CategoryService(CategoryRepository categories, DepartmentRepository departments, AuditService audit) {
        this.categories = categories;
        this.departments = departments;
        this.audit = audit;
    }

    public List<CategoryResponse> listActive() {
        return categories.findByActiveTrue(BY_LABEL).stream().map(CategoryResponse::from).toList();
    }

    public List<CategoryResponse> listAll() {
        return categories.findAll(BY_LABEL).stream().map(CategoryResponse::from).toList();
    }

    /** Every category by id, for labelling lists of issues. The table is small. */
    public Map<UUID, CategoryResponse> lookup() {
        return categories.findAll().stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toMap(CategoryResponse::id, Function.identity()));
    }

    /** Any category by code, active or not (used for filtering existing issues). */
    public Optional<CategoryResponse> findByCode(String code) {
        return categories.findByCode(code).map(CategoryResponse::from);
    }

    /** The routing rule for a new report; only active categories accept new issues. */
    public CategoryResponse routeNewIssue(String code) {
        return categories.findByCode(code)
                .filter(Category::isActive)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_CATEGORY",
                        "Category '" + code + "' does not exist or is not accepting reports."));
    }

    @Transactional
    public CategoryResponse create(Caller admin, CreateCategoryRequest request) {
        requireActiveDepartment(request.departmentId());
        if (categories.existsByCode(request.code())) {
            throw codeTaken();
        }
        try {
            Category saved = categories.saveAndFlush(new Category(request.code(), request.label().trim(),
                    request.departmentId(), request.defaultPriority()));
            audit.record(admin, "CATEGORY_CREATED", "CATEGORY", saved.getId(), "code=" + saved.getCode()
                    + ", department=" + saved.getDefaultDepartmentId() + ", priority=" + saved.getDefaultPriority());
            return CategoryResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw codeTaken();
        }
    }

    @Transactional
    public CategoryResponse update(Caller admin, UUID id, UpdateCategoryRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> ApiException.notFound("Category"));
        if (request.label() != null) {
            category.setLabel(request.label().trim());
        }
        if (request.departmentId() != null) {
            requireActiveDepartment(request.departmentId());
            category.setDefaultDepartmentId(request.departmentId());
        }
        if (request.defaultPriority() != null) {
            category.setDefaultPriority(request.defaultPriority());
        }
        if (request.active() != null) {
            if (request.active()) {
                // Re-activating must not route to a department that was switched off meanwhile
                requireActiveDepartment(category.getDefaultDepartmentId());
            }
            category.setActive(request.active());
        }
        audit.record(admin, "CATEGORY_UPDATED", "CATEGORY", id, "label=" + request.label() + ", department="
                + request.departmentId() + ", priority=" + request.defaultPriority() + ", active=" + request.active());
        return CategoryResponse.from(category);
    }

    private void requireActiveDepartment(UUID departmentId) {
        if (!departments.existsByIdAndActiveTrue(departmentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEPARTMENT_NOT_FOUND",
                    "Department was not found or is inactive.");
        }
    }

    private static ApiException codeTaken() {
        return ApiException.conflict("CODE_TAKEN", "A category with this code already exists.");
    }
}
