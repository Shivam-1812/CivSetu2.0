package com.civsetu.common.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is making the request, read from a validated access token. Public endpoints may be
 * called without a token; then the caller is anonymous.
 */
public record Caller(UUID id, String role, UUID departmentId) {

    private static final Caller ANONYMOUS = new Caller(null, null, null);

    /** Accepts {@code null} for unauthenticated requests. */
    public static Caller from(Jwt jwt) {
        if (jwt == null) {
            return ANONYMOUS;
        }
        String department = jwt.getClaimAsString(TokenClaims.DEPARTMENT);
        return new Caller(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString(TokenClaims.ROLE),
                department == null ? null : UUID.fromString(department));
    }

    public boolean isAnonymous() {
        return id == null;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /** An Authority whose department is the given one. */
    public boolean isAuthorityOf(UUID department) {
        return "AUTHORITY".equals(role) && departmentId != null && departmentId.equals(department);
    }

    public boolean is(UUID userId) {
        return id != null && id.equals(userId);
    }
}
