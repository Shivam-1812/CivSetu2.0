package com.civsetu.auth.controller;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.civsetu.auth.config.AuthProperties;

/**
 * The refresh token travels only in an httpOnly cookie scoped to /api/v1/auth,
 * so page scripts cannot read it and it is not sent with ordinary API calls.
 */
@Component
class RefreshCookies {

    private final AuthProperties properties;

    RefreshCookies(AuthProperties properties) {
        this.properties = properties;
    }

    String name() {
        return properties.refreshCookie().name();
    }

    ResponseCookie issue(String rawToken) {
        return base(rawToken).maxAge(properties.refreshTokenTtl()).build();
    }

    ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        AuthProperties.RefreshCookie cookie = properties.refreshCookie();
        return ResponseCookie.from(cookie.name(), value)
                .httpOnly(true)
                .secure(cookie.secure())
                .sameSite(cookie.sameSite())
                .path(cookie.path());
    }
}
