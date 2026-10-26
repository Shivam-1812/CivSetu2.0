package com.civsetu.department.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.dto.CreateCategoryRequest;
import com.civsetu.department.dto.UpdateCategoryRequest;
import com.civsetu.department.service.CategoryService;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
class AdminCategoryController {

    private final CategoryService categories;

    AdminCategoryController(CategoryService categories) {
        this.categories = categories;
    }

    /** All categories, including inactive ones. */
    @GetMapping
    List<CategoryResponse> list() {
        return categories.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return categories.create(request);
    }

    @PatchMapping("/{id}")
    CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categories.update(id, request);
    }
}
