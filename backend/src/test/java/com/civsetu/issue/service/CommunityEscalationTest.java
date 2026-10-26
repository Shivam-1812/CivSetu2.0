package com.civsetu.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.civsetu.support.ApiTest;

/**
 * In tests the escalation threshold is 3 net agree votes.
 */
class CommunityEscalationTest extends ApiTest {

    private void vote(String issueId, String value) throws Exception {
        mvc.perform(put("/api/v1/issues/" + issueId + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerCitizen(uniqueEmail("voter")).accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    private String detail(String issueId) throws Exception {
        return mvc.perform(get("/api/v1/issues/" + issueId)).andReturn().getResponse().getContentAsString();
    }

    private String newIssue() throws Exception {
        UUID department = createDepartment();
        staffSession("AUTHORITY", department);
        return reportIssue(registerCitizen(uniqueEmail("reporter")), "Pothole outside the hospital",
                createCategory(department, "MEDIUM"), 18.5, 73.8);
    }

    @Test
    void enoughConfirmationsRaisePriorityOnceAndBringTheDeadlineForward() throws Exception {
        String id = newIssue();
        Instant medium = Instant.parse(JsonPath.read(detail(id), "$.slaDueAt"));

        vote(id, "AGREE");
        vote(id, "AGREE");
        assertThat(JsonPath.<String>read(detail(id), "$.priority")).isEqualTo("MEDIUM");
        vote(id, "AGREE");

        String raised = detail(id);
        assertThat(JsonPath.<String>read(raised, "$.priority")).isEqualTo("HIGH");
        // HIGH is due 2 days after the report instead of 4
        assertThat(Instant.parse(JsonPath.read(raised, "$.slaDueAt"))).isBefore(medium);

        // Only once: more votes do not raise it again
        vote(id, "AGREE");
        vote(id, "AGREE");
        vote(id, "AGREE");
        assertThat(JsonPath.<String>read(detail(id), "$.priority")).isEqualTo("HIGH");

        String history = mvc.perform(get("/api/v1/issues/" + id + "/history"))
                .andReturn().getResponse().getContentAsString();
        List<String> actions = JsonPath.read(history, "$[*].action");
        assertThat(actions).containsOnlyOnce("PRIORITY_RAISED");
        assertThat(JsonPath.<List<String>>read(history, "$[?(@.action == 'PRIORITY_RAISED')].actor"))
                .containsExactly("SYSTEM");
    }

    @Test
    void disagreementsCountAgainst() throws Exception {
        String id = newIssue();
        vote(id, "DISAGREE");
        vote(id, "AGREE");
        vote(id, "AGREE");
        vote(id, "AGREE");

        // 3 agree - 1 disagree = 2, below the threshold of 3
        assertThat(JsonPath.<String>read(detail(id), "$.priority")).isEqualTo("MEDIUM");
    }
}
