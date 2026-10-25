package com.civsetu.auth.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import com.civsetu.auth.dto.AuthResponse;
import com.civsetu.auth.dto.LoginRequest;
import com.civsetu.auth.dto.RegisterRequest;
import com.civsetu.auth.service.RefreshTokenService;
import com.civsetu.auth.service.TokenService;
import com.civsetu.common.error.ApiException;
import com.civsetu.common.security.CurrentUser;
import com.civsetu.user.dto.UserResponse;
import com.civsetu.user.service.UserService;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final UserService users;
    private final TokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookies cookies;

    AuthController(UserService users, TokenService accessTokens, RefreshTokenService refreshTokens,
            RefreshCookies cookies) {
        this.users = users;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
    }

    /** Creates a Citizen account and signs it in. */
    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = users.registerCitizen(request.name(), request.email(), request.password(),
                request.phone());
        return session(HttpStatus.CREATED, user, refreshTokens.issue(user.id()));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        UserResponse user = users.authenticate(request.email(), request.password());
        return session(HttpStatus.OK, user, refreshTokens.issue(user.id()));
    }

    /** Exchanges the refresh cookie for a new access token and a rotated refresh cookie. */
    @PostMapping("/refresh")
    ResponseEntity<?> refresh(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookies.name());
        if (cookie == null || cookie.getValue().isBlank()) {
            return failure(new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_MISSING",
                    "Please sign in again."));
        }
        try {
            RefreshTokenService.Rotation rotation = refreshTokens.rotate(cookie.getValue());
            UserResponse user = users.getActiveUser(rotation.userId());
            return session(HttpStatus.OK, user, rotation.rawToken());
        } catch (ApiException e) {
            // Clear the dead cookie so the client stops retrying with it
            return failure(e);
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookies.name());
        if (cookie != null && !cookie.getValue().isBlank()) {
            refreshTokens.revoke(cookie.getValue());
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.getActiveUser(CurrentUser.id(jwt));
    }

    private ResponseEntity<AuthResponse> session(HttpStatus status, UserResponse user, String rawRefreshToken) {
        TokenService.AccessToken access = accessTokens.issue(user);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(rawRefreshToken).toString())
                .body(new AuthResponse(access.value(), "Bearer", access.expiresInSeconds(), user));
    }

    private ResponseEntity<ProblemDetail> failure(ApiException e) {
        return ResponseEntity.status(e.getStatus())
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(e.toProblemDetail());
    }
}
