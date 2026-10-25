package com.civsetu.auth.dto;

import com.civsetu.user.dto.UserResponse;

/**
 * Access token for the Authorization header; the refresh token is set as a cookie instead.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
