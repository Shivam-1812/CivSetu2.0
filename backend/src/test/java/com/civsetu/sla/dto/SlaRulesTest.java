package com.civsetu.sla.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.civsetu.common.domain.Priority;

/**
 * MEDIUM: 96 hours, at risk once half the time (48 hours) is left. "Now" is fixed.
 */
class SlaRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final SlaRules RULES = new SlaRules(Map.of(Priority.MEDIUM, Duration.ofHours(96)),
            Map.of(Priority.MEDIUM, new BigDecimal("0.50")));

    private static SlaState open(Duration left) {
        return RULES.state(Priority.MEDIUM, NOW.plus(left), null, true, NOW);
    }

    @Test
    void openIssuesMoveFromOnTrackToAtRiskToBreached() {
        assertThat(open(Duration.ofHours(60))).isEqualTo(SlaState.ON_TRACK);
        assertThat(open(Duration.ofHours(48).plusSeconds(1))).isEqualTo(SlaState.ON_TRACK);
        assertThat(open(Duration.ofHours(48))).isEqualTo(SlaState.AT_RISK);
        assertThat(open(Duration.ofSeconds(1))).isEqualTo(SlaState.AT_RISK);
        assertThat(open(Duration.ZERO)).isEqualTo(SlaState.BREACHED);
        assertThat(open(Duration.ofHours(-5))).isEqualTo(SlaState.BREACHED);
    }

    @Test
    void closedIssuesAreJudgedByWhenTheyWereResolved() {
        Instant due = NOW.minus(Duration.ofHours(1));
        assertThat(RULES.state(Priority.MEDIUM, due, due, false, NOW)).isEqualTo(SlaState.MET);
        assertThat(RULES.state(Priority.MEDIUM, due, due.minusSeconds(60), false, NOW)).isEqualTo(SlaState.MET);
        assertThat(RULES.state(Priority.MEDIUM, due, due.plusSeconds(1), false, NOW)).isEqualTo(SlaState.MISSED);
        // Rejected or duplicate: no SLA outcome
        assertThat(RULES.state(Priority.MEDIUM, due, null, false, NOW)).isNull();
    }
}
