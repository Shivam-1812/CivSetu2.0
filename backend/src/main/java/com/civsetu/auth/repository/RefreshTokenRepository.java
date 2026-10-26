package com.civsetu.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civsetu.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Row lock so two concurrent refreshes of one token cannot both rotate it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Unlinks tokens that point at a token about to be deleted (the replaced-by chain). */
    @Modifying
    @Query(value = "UPDATE refresh_tokens SET replaced_by_id = NULL WHERE replaced_by_id IN "
            + "(SELECT id FROM refresh_tokens WHERE expires_at < :cutoff)", nativeQuery = true)
    int unlinkExpired(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
