package com.civsetu.user;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.civsetu.common.security.CurrentUser;

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
