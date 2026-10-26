package com.civsetu.issue.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civsetu.issue.entity.Issue;

public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

    /** Demo data only: places a seeded issue in the past (auditing always stamps "now"). */
    @Modifying
    @Query(value = "UPDATE issues SET created_at = :createdAt, updated_at = :createdAt, sla_due_at = :slaDueAt "
            + "WHERE id = :id", nativeQuery = true)
    void backdate(@Param("id") UUID id, @Param("createdAt") Instant createdAt, @Param("slaDueAt") Instant slaDueAt);
}
