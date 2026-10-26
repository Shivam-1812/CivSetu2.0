package com.civsetu.triage.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civsetu.triage.entity.TriageDecision;

public interface TriageDecisionRepository extends JpaRepository<TriageDecision, UUID> {

    Optional<TriageDecision> findFirstByIssueIdOrderByCreatedAtDesc(UUID issueId);
}
