package com.civsetu.engagement.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civsetu.engagement.entity.IssueComment;

public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

    Page<IssueComment> findByIssueId(UUID issueId, Pageable pageable);

    long countByIssueId(UUID issueId);

    long countByIssueIdAndUserId(UUID issueId, UUID userId);

    /** Rows of [issueId, count] for several issues at once. */
    @Query("select c.issueId, count(c) from IssueComment c where c.issueId in :issueIds group by c.issueId")
    List<Object[]> countByIssueIds(@Param("issueIds") Collection<UUID> issueIds);
}
