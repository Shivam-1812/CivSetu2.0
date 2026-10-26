package com.civsetu.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.civsetu.support.ApiTest;

class RefreshTokenCleanupTest extends ApiTest {

    @Autowired
    RefreshTokenCleanup cleanup;

    private UUID token(UUID userId, UUID family, String expiresIn, UUID replacedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO refresh_tokens (id, user_id, family_id, token_hash, expires_at, revoked_at, "
                        + "replaced_by_id) VALUES (?, ?, ?, ?, now() + CAST(? AS interval), "
                        + "CASE WHEN CAST(? AS uuid) IS NULL THEN NULL ELSE now() END, ?)",
                id, userId, family, UUID.randomUUID().toString().replace("-", "") + "00000000000000000000000000000000",
                expiresIn, replacedBy, replacedBy);
        return id;
    }

    @Test
    void expiredTokensAreDeletedAndValidOnesKept() throws Exception {
        UUID user = UUID.fromString(registerCitizen(uniqueEmail("tokens")).userId());
        UUID family = UUID.randomUUID();
        // A rotation chain: the old token points at its (also expired) replacement
        UUID newer = token(user, family, "-1 day", null);
        UUID older = token(user, family, "-2 days", newer);
        UUID valid = token(user, UUID.randomUUID(), "7 days", null);

        assertThat(cleanup.deleteExpired()).isGreaterThanOrEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE id IN (?, ?)", Long.class,
                older, newer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE id = ?", Long.class, valid))
                .isEqualTo(1);
    }
}
