package com.civsetu.common.security;

/**
 * Claim names inside CivSetu access tokens. Written by the auth feature, read everywhere.
 */
public final class TokenClaims {

    /** CITIZEN, AUTHORITY or ADMIN. */
    public static final String ROLE = "role";

    /** Department id; present only for AUTHORITY users. */
    public static final String DEPARTMENT = "dept";

    private TokenClaims() {
    }
}
