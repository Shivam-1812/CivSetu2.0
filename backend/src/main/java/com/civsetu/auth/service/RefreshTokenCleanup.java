package com.civsetu.auth.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.auth.repository.RefreshTokenRepository;

/**
 * Deletes refresh tokens once they have expired. Expired tokens are useless for signing in and for
 * detecting reuse, so keeping them would only grow the table. Runs nightly.
 */
@Component
class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository tokens;
    private final Clock clock;

    RefreshTokenCleanup(RefreshTokenRepository tokens, Clock clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    @Scheduled(cron = "${civsetu.auth.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public int deleteExpired() {
        Instant now = clock.instant();
        tokens.unlinkExpired(now);
        int deleted = tokens.deleteExpired(now);
        if (deleted > 0) {
            log.info("Deleted {} expired refresh token(s)", deleted);
        }
        return deleted;
    }
}
