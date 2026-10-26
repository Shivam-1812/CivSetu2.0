package com.civsetu.department.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.service.CategoryService;
import com.civsetu.department.service.DepartmentService;

/**
 * Public reference data for report forms and filters: active categories and departments.
 */
@RestController
@RequestMapping("/api/v1")
class RoutingController {

    private final CategoryService categories;
    private final DepartmentService departments;

    RoutingController(CategoryService categories, DepartmentService departments) {
        this.categories = categories;
        this.departments = departments;
    }

    @GetMapping("/categories")
    List<CategoryResponse> categories() {
        return categories.listActive();
    }

    @GetMapping("/departments")
    List<DepartmentResponse> departments() {
        return departments.listActive();
    }
}
