package com.civsetu.user.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.web.PageResponse;
import com.civsetu.user.dto.CreateStaffRequest;
import com.civsetu.user.dto.UserResponse;
import com.civsetu.user.entity.Role;
import com.civsetu.user.service.UserService;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController {

    private final UserService users;

    AdminUserController(UserService users) {
        this.users = users;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateStaffRequest request) {
        return users.createStaff(request);
    }

    @GetMapping
    PageResponse<UserResponse> list(@RequestParam(required = false) Role role, Pageable pageable) {
        return users.list(role, pageable);
    }
}
