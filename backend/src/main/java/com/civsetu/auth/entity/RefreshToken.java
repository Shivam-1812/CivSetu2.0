package com.civsetu.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.civsetu.common.persistence.BaseEntity;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

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

    public RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant when, UUID replacedBy) {
        this.revokedAt = when;
        this.replacedById = replacedBy;
    }
}
