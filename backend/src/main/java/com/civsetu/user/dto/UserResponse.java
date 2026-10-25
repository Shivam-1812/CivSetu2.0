package com.civsetu.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.civsetu.user.entity.Role;
import com.civsetu.user.entity.User;

/**
 * Public view of an account. The password hash never leaves the user feature.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        String phone,
        Role role,
        UUID departmentId,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(),
                user.getDepartmentId(), user.isActive(), user.getCreatedAt());
    }
}
