package com.civsetu.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.civsetu.support.ApiTest;

class SlaMonitorTest extends ApiTest {

    @Autowired
    SlaMonitor monitor;

    private long notifications(UUID userId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?", Long.class,
                userId, type);
    }

    @Test
    void breachIsAlertedExactlyOnceEvenWhenTheMonitorRunsAgain() throws Exception {
        UUID department = createDepartment();
        Session authority = staffSession("AUTHORITY", department);
        Session admin = adminSession();
        Session reporter = registerCitizen(uniqueEmail("late"));
        String id = reportIssue(reporter, "Blocked storm drain", createCategory(department, "HIGH"), 18.5, 73.8);
        String url = "/api/v1/issues/" + id;
        assertThat(JsonPath.<String>read(mvc.perform(get(url)).andReturn().getResponse().getContentAsString(),
                "$.slaState")).isEqualTo("ON_TRACK");

        jdbc.update("UPDATE issues SET sla_due_at = now() - interval '2 hours' WHERE id = ?", UUID.fromString(id));
        assertThat(JsonPath.<String>read(mvc.perform(get(url)).andReturn().getResponse().getContentAsString(),
                "$.slaState")).isEqualTo("BREACHED");

        monitor.checkBreaches();
        // A second run, or a run after a restart, finds nothing new for this issue
        monitor.checkBreaches();

        String history = mvc.perform(get(url + "/history")).andReturn().getResponse().getContentAsString();
        List<String> actions = JsonPath.read(history, "$[*].action");
        assertThat(actions).containsOnlyOnce("SLA_BREACHED");
        assertThat(notifications(UUID.fromString(reporter.userId()), "SLA_BREACHED")).isEqualTo(1);
        assertThat(notifications(UUID.fromString(authority.userId()), "SLA_BREACHED")).isEqualTo(1);
        assertThat(notifications(UUID.fromString(admin.userId()), "SLA_BREACHED")).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT breach_notified_at IS NOT NULL FROM issues WHERE id = ?",
                Boolean.class, UUID.fromString(id))).isTrue();

        // Overdue issues show up in the department's analytics
        mvc.perform(get("/api/v1/analytics/overdue").header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken())))
                .andExpect(jsonPath("$.content[*].id").value(hasItem(id)));
    }

    @Test
    void issuesWithinTheirDeadlineAreLeftAlone() throws Exception {
        UUID department = createDepartment();
        staffSession("AUTHORITY", department);
        String id = reportIssue(registerCitizen(uniqueEmail("ok")), "Faded zebra crossing",
                createCategory(department, "LOW"), 18.5, 73.8);

        monitor.checkBreaches();

        assertThat(jdbc.queryForObject("SELECT breach_notified_at IS NULL FROM issues WHERE id = ?", Boolean.class,
                UUID.fromString(id))).isTrue();
    }
}
