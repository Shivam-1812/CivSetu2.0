package com.civsetu.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.civsetu.user.UserResponse;

/**
 * Issues short-lived access tokens. Claims: sub (user id), role, dept (Authority only).
 */
@Service
class TokenService {

    static final String ROLE_CLAIM = "role";
    static final String DEPARTMENT_CLAIM = "dept";

    record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    TokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    AccessToken issue(UserResponse user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLE_CLAIM, user.role().name());
        if (user.departmentId() != null) {
            claims.claim(DEPARTMENT_CLAIM, user.departmentId().toString());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(value, expiresAt, properties.accessTokenTtl().toSeconds());
    }
}
