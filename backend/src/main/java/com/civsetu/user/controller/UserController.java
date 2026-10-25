package com.civsetu.user.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.security.CurrentUser;
import com.civsetu.user.dto.UpdateProfileRequest;
import com.civsetu.user.dto.UserResponse;
import com.civsetu.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @PatchMapping("/me")
    UserResponse updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        return users.updateProfile(CurrentUser.id(jwt), request);
    }
}
