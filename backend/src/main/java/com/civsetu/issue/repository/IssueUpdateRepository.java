package com.civsetu.issue.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.issue.entity.IssueUpdate;

public interface IssueUpdateRepository extends JpaRepository<IssueUpdate, UUID> {

    List<IssueUpdate> findByIssueIdOrderByCreatedAtAscIdAsc(UUID issueId);
}
