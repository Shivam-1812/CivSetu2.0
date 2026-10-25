package com.civsetu.user;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of an account. The password hash never leaves the user package.
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

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(),
                user.getDepartmentId(), user.isActive(), user.getCreatedAt());
    }
}
