package com.civsetu.triage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.civsetu.common.domain.Priority;
import com.civsetu.department.dto.CategoryResponse;

class RuleEngineTest {

    private final RuleEngine rules = new RuleEngine();

    private static CategoryResponse category(Priority priority) {
        return new CategoryResponse(UUID.randomUUID(), "STREETLIGHT", "Streetlight not working", UUID.randomUUID(),
                priority, true);
    }

    @Test
    void usesTheCategoryDefault() {
        RuleEngine.Outcome outcome = rules.priorityFor(category(Priority.MEDIUM), "Light out", "Dark lane at night");
        assertThat(outcome.priority()).isEqualTo(Priority.MEDIUM);
        assertThat(outcome.reason()).isEqualTo("Category 'Streetlight not working' is medium priority.");
    }

    @Test
    void dangerWordsRaisePriorityOneLevel() {
        RuleEngine.Outcome outcome = rules.priorityFor(category(Priority.MEDIUM), "Pole sparking",
                "The streetlight pole is sparking near the bus stop.");
        assertThat(outcome.priority()).isEqualTo(Priority.HIGH);
        assertThat(outcome.reason()).contains("raised to high", "'sparking'");
    }

    @Test
    void wordsAreMatchedWhole() {
        // "fireworks" is not "fire", "latest" is not a danger word, "smokers" is not "smoke"
        RuleEngine.Outcome outcome = rules.priorityFor(category(Priority.LOW), "Latest complaint",
                "Fireworks debris left by smokers in the park.");
        assertThat(outcome.priority()).isEqualTo(Priority.LOW);
    }

    @Test
    void criticalStaysCritical() {
        assertThat(rules.priorityFor(category(Priority.CRITICAL), "Live wire", "Live wire on road").priority())
                .isEqualTo(Priority.CRITICAL);
    }
}
