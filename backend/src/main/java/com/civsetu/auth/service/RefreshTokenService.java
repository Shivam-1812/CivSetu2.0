package com.civsetu.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.auth.config.AuthProperties;
import com.civsetu.auth.entity.RefreshToken;
import com.civsetu.auth.repository.RefreshTokenRepository;
import com.civsetu.common.error.ApiException;

/**
 * Rotating refresh tokens. Each refresh revokes the presented token and issues a new one in the
 * same family. Presenting a token that was already rotated means it leaked, so the whole family
 * (that login session) is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    public record Rotation(UUID userId, String rawToken) {
    }

    private final RefreshTokenRepository tokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenService(RefreshTokenRepository tokens, AuthProperties properties, Clock clock) {
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new login session; returns the raw token for the cookie. */
    @Transactional
    public String issue(UUID userId) {
        return create(userId, UUID.randomUUID()).rawToken();
    }

    // noRollbackFor: a reuse detection must commit the family revocation before reporting 401
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        RefreshToken current = tokens.findByTokenHash(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);
        Instant now = clock.instant();
        if (current.isRevoked()) {
            int revoked = tokens.revokeFamily(current.getFamilyId(), now);
            log.warn("Refresh token reuse for user {}; revoked {} token(s) in its session", current.getUserId(),
                    revoked);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSED",
                    "This session was ended for security reasons. Please sign in again.");
        }
        if (!now.isBefore(current.getExpiresAt())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED",
                    "Your session has expired. Please sign in again.");
        }
        Created next = create(current.getUserId(), current.getFamilyId());
        current.revoke(now, next.id());
        return new Rotation(current.getUserId(), next.rawToken());
    }

    /** Logout: revokes the presented token's whole session. Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private record Created(UUID id, String rawToken) {
    }

    private Created create(UUID userId, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        RefreshToken saved = tokens.save(new RefreshToken(userId, familyId, hash(raw), expiresAt));
        return new Created(saved.getId(), raw);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Please sign in again.");
    }
}
