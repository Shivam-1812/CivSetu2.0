package com.civsetu.engagement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civsetu.engagement.entity.IssueVote;
import com.civsetu.engagement.entity.VoteValue;

public interface IssueVoteRepository extends JpaRepository<IssueVote, UUID> {

    /** Insert or change the user's vote; the unique constraint makes repeats harmless (idempotent). */
    @Modifying
    @Query(value = "INSERT INTO issue_votes (issue_id, user_id, value) VALUES (:issueId, :userId, :value) "
            + "ON CONFLICT (issue_id, user_id) DO UPDATE SET value = EXCLUDED.value, updated_at = now()",
            nativeQuery = true)
    void upsert(@Param("issueId") UUID issueId, @Param("userId") UUID userId, @Param("value") String value);

    @Modifying
    @Query("delete from IssueVote v where v.issueId = :issueId and v.userId = :userId")
    int deleteVote(@Param("issueId") UUID issueId, @Param("userId") UUID userId);

    @Query("select v.value, count(v) from IssueVote v where v.issueId = :issueId group by v.value")
    List<Object[]> countByValue(@Param("issueId") UUID issueId);

    @Query("select v.value from IssueVote v where v.issueId = :issueId and v.userId = :userId")
    Optional<VoteValue> findValue(@Param("issueId") UUID issueId, @Param("userId") UUID userId);
}
