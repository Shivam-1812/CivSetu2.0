package com.civsetu.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

@Entity
@Table(name = "refresh_tokens")
class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // All tokens rotated from one login share a family
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    // SHA-256 of the raw token; the raw value only ever lives in the client's cookie
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    protected RefreshToken() {
    }

    RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getFamilyId() {
        return familyId;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    void revoke(Instant when, UUID replacedBy) {
        this.revokedAt = when;
        this.replacedById = replacedBy;
    }
}
