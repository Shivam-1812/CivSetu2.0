package com.civsetu.engagement.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.engagement.entity.IssueComment;

public interface IssueCommentRepository extends JpaRepository<IssueComment, UUID> {

    Page<IssueComment> findByIssueId(UUID issueId, Pageable pageable);

    long countByIssueId(UUID issueId);

    long countByIssueIdAndUserId(UUID issueId, UUID userId);
}
