package com.civsetu.common.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the caller's identity from a validated access token. The subject is the user id.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
