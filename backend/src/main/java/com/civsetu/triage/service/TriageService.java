package com.civsetu.triage.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.civsetu.common.domain.Priority;
import com.civsetu.department.dto.CategoryResponse;
import com.civsetu.department.dto.DepartmentResponse;
import com.civsetu.department.service.CategoryService;
import com.civsetu.department.service.DepartmentService;
import com.civsetu.triage.config.TriageProperties;
import com.civsetu.triage.dto.TriageDecisionResponse;
import com.civsetu.triage.dto.TriageInput;
import com.civsetu.triage.entity.TriageDecision;
import com.civsetu.triage.entity.TriageSource;
import com.civsetu.triage.repository.TriageDecisionRepository;

/**
 * Decides where a report goes and how urgent it is. The AI advisor (if configured) is asked
 * first; its answer is used only if every field is valid and confident enough. Otherwise the
 * rule engine decides. Every decision is stored with its source and reason.
 *
 * <p>Not transactional on purpose: the AI call can take seconds and must not hold a database
 * connection. Only the final save runs in a (short) transaction.
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final TriageDecisionRepository decisions;
    private final CategoryService categories;
    private final DepartmentService departments;
    private final RuleEngine rules;
    private final ObjectProvider<TriageAdvisor> advisor;
    private final TriageProperties properties;
    private final TransactionTemplate saving;

    TriageService(TriageDecisionRepository decisions, CategoryService categories, DepartmentService departments,
            RuleEngine rules, ObjectProvider<TriageAdvisor> advisor, TriageProperties properties,
            PlatformTransactionManager transactions) {
        this.decisions = decisions;
        this.categories = categories;
        this.departments = departments;
        this.rules = rules;
        this.advisor = advisor;
        this.properties = properties;
        // A fresh transaction: decide() may be called from another transaction's after-commit callback
        this.saving = new TransactionTemplate(transactions);
        this.saving.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public TriageDecisionResponse decide(TriageInput input) {
        Map<UUID, CategoryResponse> allCategories = categories.lookup();
        List<CategoryResponse> activeCategories = categories.listActive();
        List<DepartmentResponse> allowedDepartments = departments.listActive().stream()
                .filter(department -> !input.excludedDepartments().contains(department.id()))
                .toList();
        CategoryResponse hint = allCategories.get(input.categoryId());

        TriageAdvisor ai = advisor.getIfAvailable();
        String fallbackReason = null;
        if (ai != null && !allowedDepartments.isEmpty()) {
            long started = System.nanoTime();
            try {
                TriageAdvisor.Answer answer = ai.advise(new TriageAdvisor.Request(input.title(), input.description(),
                        hint.code(), input.address(), input.images(), activeCategories, allowedDepartments));
                int latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
                Optional<TriageDecision> accepted = accept(answer, input.issueId(), ai.model(), activeCategories,
                        allowedDepartments, latencyMs);
                if (accepted.isPresent()) {
                    return save(accepted.get());
                }
                fallbackReason = "AI answer rejected: " + rejection(answer, activeCategories, allowedDepartments);
            } catch (RuntimeException e) {
                fallbackReason = "AI unavailable: " + e.getClass().getSimpleName();
                log.warn("Triage AI failed for issue {}; using rules", input.issueId(), e);
            }
        }
        return save(byRules(input, hint, allowedDepartments, fallbackReason));
    }

    private TriageDecisionResponse save(TriageDecision decision) {
        return saving.execute(status -> TriageDecisionResponse.from(decisions.save(decision)));
    }

    /** How many report photos the AI should see (0 when no AI is configured: rules ignore photos). */
    public int imagesWanted() {
        return advisor.getIfAvailable() == null ? 0 : properties.maxImages();
    }

    /** The most recent decision for an issue, e.g. to explain its priority. */
    @Transactional(readOnly = true)
    public Optional<TriageDecisionResponse> latest(UUID issueId) {
        return decisions.findFirstByIssueIdOrderByCreatedAtDesc(issueId).map(TriageDecisionResponse::from);
    }

    private Optional<TriageDecision> accept(TriageAdvisor.Answer answer, UUID issueId, String model,
            List<CategoryResponse> activeCategories, List<DepartmentResponse> allowedDepartments, int latencyMs) {
        if (rejection(answer, activeCategories, allowedDepartments) != null) {
            return Optional.empty();
        }
        CategoryResponse category = activeCategories.stream()
                .filter(candidate -> candidate.code().equals(answer.categoryCode())).findFirst().orElseThrow();
        DepartmentResponse department = allowedDepartments.stream()
                .filter(candidate -> candidate.code().equals(answer.departmentCode())).findFirst().orElseThrow();
        return Optional.of(new TriageDecision(issueId, TriageSource.AI, model, category.id(), department.id(),
                Priority.valueOf(answer.priority()),
                BigDecimal.valueOf(answer.confidence()).setScale(2, RoundingMode.HALF_UP),
                truncate(answer.reason()), Boolean.TRUE.equals(answer.looksInvalid()), null, latencyMs));
    }

    /** Why an AI answer cannot be used, or null if it can. */
    private String rejection(TriageAdvisor.Answer answer, List<CategoryResponse> activeCategories,
            List<DepartmentResponse> allowedDepartments) {
        if (answer == null) {
            return "empty answer";
        }
        if (activeCategories.stream().noneMatch(category -> category.code().equals(answer.categoryCode()))) {
            return "unknown category " + answer.categoryCode();
        }
        if (allowedDepartments.stream().noneMatch(department -> department.code().equals(answer.departmentCode()))) {
            return "department not allowed " + answer.departmentCode();
        }
        try {
            Priority.valueOf(String.valueOf(answer.priority()));
        } catch (IllegalArgumentException e) {
            return "unknown priority " + answer.priority();
        }
        if (answer.confidence() == null || answer.confidence() < properties.minConfidence()
                || answer.confidence() > 1) {
            return "confidence " + answer.confidence() + " outside " + properties.minConfidence() + "-1";
        }
        if (answer.reason() == null || answer.reason().isBlank()) {
            return "no reason given";
        }
        return null;
    }

    private TriageDecision byRules(TriageInput input, CategoryResponse hint,
            List<DepartmentResponse> allowedDepartments, String fallbackReason) {
        RuleEngine.Outcome outcome = rules.priorityFor(hint, input.title(), input.description());
        UUID department = allowedDepartments.stream()
                .map(DepartmentResponse::id)
                .filter(id -> id.equals(hint.departmentId()))
                .findFirst()
                .orElseGet(() -> fallbackDepartment(allowedDepartments));
        String reason = outcome.reason();
        if (!department.equals(hint.departmentId())) {
            reason += " Sent to the fallback department because the category's department already returned it.";
        }
        return new TriageDecision(input.issueId(), TriageSource.RULE, null, hint.id(), department,
                outcome.priority(), null, reason, false, fallbackReason, null);
    }

    /** The configured fallback department; if it already returned the issue, the first remaining one. */
    private UUID fallbackDepartment(List<DepartmentResponse> allowedDepartments) {
        List<DepartmentResponse> active = departments.listActive();
        return allowedDepartments.stream()
                .filter(department -> department.code().equals(properties.fallbackDepartment()))
                .findFirst()
                .or(() -> allowedDepartments.stream().findFirst())
                // Every department returned it: keep it with the fallback department rather than drop it
                .or(() -> active.stream()
                        .filter(department -> department.code().equals(properties.fallbackDepartment()))
                        .findFirst())
                .map(DepartmentResponse::id)
                .orElseThrow(() -> new IllegalStateException("No active department to route to"));
    }

    private static String truncate(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= MAX_REASON_LENGTH ? trimmed : trimmed.substring(0, MAX_REASON_LENGTH);
    }
}
