package com.civsetu.sla.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.common.domain.Priority;
import com.civsetu.sla.repository.SlaPolicyRepository;

/**
 * Resolution deadlines from the {@code sla_policies} table (CRITICAL 1 day, HIGH 2, MEDIUM 4, LOW 7 by default).
 * At-risk/breach tracking and the scheduler arrive in Phase 7.
 */
@Service
@Transactional(readOnly = true)
public class SlaService {

    private final SlaPolicyRepository policies;

    SlaService(SlaPolicyRepository policies) {
        this.policies = policies;
    }

    public Instant dueAt(Priority priority, Instant from) {
        return policies.findById(priority)
                .map(policy -> from.plus(policy.getTarget()))
                .orElseThrow(() -> new IllegalStateException("No SLA policy for priority " + priority));
    }
}
