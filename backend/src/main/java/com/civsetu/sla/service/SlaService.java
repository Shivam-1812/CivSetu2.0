package com.civsetu.sla.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civsetu.common.domain.Priority;
import com.civsetu.sla.dto.SlaRules;
import com.civsetu.sla.entity.SlaPolicy;
import com.civsetu.sla.repository.SlaPolicyRepository;

/**
 * Resolution deadlines from the {@code sla_policies} table: CRITICAL 1 day, HIGH 2, MEDIUM 4, LOW 7.
 * The targets are fixed system configuration; nobody can change an issue's deadline by hand.
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

    /** All targets at once, for evaluating SLA states of many issues. */
    public SlaRules rules() {
        Map<Priority, Duration> targets = new EnumMap<>(Priority.class);
        Map<Priority, BigDecimal> ratios = new EnumMap<>(Priority.class);
        for (SlaPolicy policy : policies.findAll()) {
            targets.put(policy.getPriority(), policy.getTarget());
            ratios.put(policy.getPriority(), policy.getAtRiskRatio());
        }
        return new SlaRules(targets, ratios);
    }
}
