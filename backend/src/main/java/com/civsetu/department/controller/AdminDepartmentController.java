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

import com.civsetu.department.dto.CreateDepartmentRequest;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.dto.UpdateDepartmentRequest;
import com.civsetu.department.service.DepartmentService;

@RestController
@RequestMapping("/api/v1/admin/departments")
@PreAuthorize("hasRole('ADMIN')")
class AdminDepartmentController {

    private final DepartmentService departments;

    AdminDepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    /** All departments, including inactive ones. */
    @GetMapping
    List<DepartmentResponse> list() {
        return departments.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DepartmentResponse create(@Valid @RequestBody CreateDepartmentRequest request) {
        return departments.create(request);
    }

    @PatchMapping("/{id}")
    DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return departments.update(id, request);
    }
}
