package com.civsetu.issue.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.issue.entity.IssueAssignment;

public interface IssueAssignmentRepository extends JpaRepository<IssueAssignment, UUID> {

    List<IssueAssignment> findByIssueIdOrderByCreatedAtAsc(UUID issueId);

    Optional<IssueAssignment> findFirstByIssueIdAndEndedAtIsNullOrderByCreatedAtDesc(UUID issueId);
}
