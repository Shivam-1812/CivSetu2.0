package com.civsetu.triage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;

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
 * The AI's answer is only used when every part of it is valid; anything else falls back to the rules.
 */
class TriageServiceTest {

    private static final DepartmentResponse ROADS = new DepartmentResponse(UUID.randomUUID(), "ROADS", "Roads", null, true);
    private static final DepartmentResponse WATER = new DepartmentResponse(UUID.randomUUID(), "WATER", "Water", null, true);
    private static final DepartmentResponse GENERAL =
            new DepartmentResponse(UUID.randomUUID(), "GENERAL", "General", null, true);
    private static final CategoryResponse POTHOLE =
            new CategoryResponse(UUID.randomUUID(), "POTHOLE", "Pothole", ROADS.id(), Priority.MEDIUM, true);
    private static final CategoryResponse WATER_LEAK =
            new CategoryResponse(UUID.randomUUID(), "WATER_LEAK", "Water leak", WATER.id(), Priority.HIGH, true);

    private TriageAdvisor advisor;
    private TriageService service;
    private TriageService rulesOnly;

    @BeforeEach
    void setUp() {
        TriageDecisionRepository decisions = mock(TriageDecisionRepository.class);
        when(decisions.save(any())).thenAnswer(call -> call.getArgument(0, TriageDecision.class));
        CategoryService categories = mock(CategoryService.class);
        when(categories.lookup()).thenReturn(Map.of(POTHOLE.id(), POTHOLE, WATER_LEAK.id(), WATER_LEAK));
        when(categories.listActive()).thenReturn(List.of(POTHOLE, WATER_LEAK));
        DepartmentService departments = mock(DepartmentService.class);
        when(departments.listActive()).thenReturn(List.of(ROADS, WATER, GENERAL));
        TriageProperties properties = new TriageProperties(Duration.ofSeconds(5), 0.6, 3, "GENERAL",
                new TriageProperties.Gemini("key", "test-model", "http://localhost"));

        advisor = mock(TriageAdvisor.class);
        when(advisor.model()).thenReturn("test-model");
        service = new TriageService(decisions, categories, departments, new RuleEngine(), provider(advisor),
                properties, mock(PlatformTransactionManager.class));
        rulesOnly = new TriageService(decisions, categories, departments, new RuleEngine(), provider(null),
                properties, mock(PlatformTransactionManager.class));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TriageAdvisor> provider(TriageAdvisor advisor) {
        ObjectProvider<TriageAdvisor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(advisor);
        return provider;
    }

    private static TriageInput report(CategoryResponse hint, String description, Set<UUID> excluded) {
        return new TriageInput(UUID.randomUUID(), "Report title", description, hint.id(), "Somewhere, Pune",
                List.of(), excluded);
    }

    private void aiAnswers(String category, String department, String priority, Double confidence) {
        when(advisor.advise(any())).thenReturn(new TriageAdvisor.Answer(category, department, priority, confidence,
                "Water is pouring from a broken main.", false));
    }

    @Test
    void validAiAnswerIsUsedAndMayCorrectTheCategory() {
        aiAnswers("WATER_LEAK", "WATER", "CRITICAL", 0.92);

        TriageDecisionResponse decision = service.decide(report(POTHOLE, "Road flooded by a burst pipe", Set.of()));

        assertThat(decision.source()).isEqualTo(TriageSource.AI);
        assertThat(decision.categoryId()).isEqualTo(WATER_LEAK.id());
        assertThat(decision.departmentId()).isEqualTo(WATER.id());
        assertThat(decision.priority()).isEqualTo(Priority.CRITICAL);
        assertThat(decision.reason()).isEqualTo("Water is pouring from a broken main.");
    }

    @Test
    void unusableAiAnswersFallBackToRules() {
        for (Runnable answer : List.<Runnable>of(
                () -> aiAnswers("WATER_LEAK", "POLICE", "HIGH", 0.9),       // unknown department
                () -> aiAnswers("NOT_A_CATEGORY", "WATER", "HIGH", 0.9),    // unknown category
                () -> aiAnswers("WATER_LEAK", "WATER", "URGENT", 0.9),      // unknown priority
                () -> aiAnswers("WATER_LEAK", "WATER", "HIGH", 0.3),        // not confident enough
                () -> aiAnswers("WATER_LEAK", "WATER", "HIGH", null),       // no confidence
                () -> when(advisor.advise(any())).thenThrow(new IllegalStateException("timeout")))) {
            answer.run();
            TriageDecisionResponse decision = service.decide(report(POTHOLE, "Big hole", Set.of()));
            assertThat(decision.source()).isEqualTo(TriageSource.RULE);
            assertThat(decision.departmentId()).isEqualTo(ROADS.id());
            assertThat(decision.priority()).isEqualTo(Priority.MEDIUM);
        }
    }

    @Test
    void aiCannotSendTheIssueBackToADepartmentThatReturnedIt() {
        aiAnswers("POTHOLE", "ROADS", "MEDIUM", 0.95);

        TriageDecisionResponse decision = service.decide(report(POTHOLE, "Big hole", Set.of(ROADS.id())));

        assertThat(decision.source()).isEqualTo(TriageSource.RULE);
        // The category's own department returned it, so the rules use the fallback department
        assertThat(decision.departmentId()).isEqualTo(GENERAL.id());
    }

    @Test
    void withoutAiTheRulesDecide() {
        TriageDecisionResponse decision = rulesOnly.decide(report(POTHOLE, "Huge pothole caused an accident",
                Set.of()));

        assertThat(decision.source()).isEqualTo(TriageSource.RULE);
        assertThat(decision.departmentId()).isEqualTo(ROADS.id());
        assertThat(decision.priority()).isEqualTo(Priority.HIGH);
        assertThat(decision.reason()).contains("'accident'");
        assertThat(rulesOnly.imagesWanted()).isZero();
        assertThat(service.imagesWanted()).isEqualTo(3);
    }
}
