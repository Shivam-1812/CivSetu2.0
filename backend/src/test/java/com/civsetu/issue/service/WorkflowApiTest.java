package com.civsetu.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.civsetu.support.ApiTest;
import com.civsetu.support.TestFiles;

/**
 * The citizen → system → authority → citizen loop through the real API. Each test creates its own
 * department and category so assignment counts are not affected by other tests.
 */
class WorkflowApiTest extends ApiTest {

    @Autowired
    WorkflowSweeper sweeper;

    private record Department(UUID id, String category, List<Session> authorities) {
    }

    private Department department(int authorities) throws Exception {
        UUID id = createDepartment();
        String category = createCategory(id, "MEDIUM");
        List<Session> staff = new ArrayList<>();
        for (int i = 0; i < authorities; i++) {
            staff.add(staffSession("AUTHORITY", id));
        }
        return new Department(id, category, staff);
    }

    private String detail(String issueId, Session viewer) throws Exception {
        var request = get("/api/v1/issues/" + issueId);
        if (viewer != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(viewer.accessToken()));
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long version(String issueId) throws Exception {
        return JsonPath.<Number>read(detail(issueId, null), "$.version").longValue();
    }

    private String assignee(String issueId) throws Exception {
        return JsonPath.read(detail(issueId, adminSession()), "$.assignedAuthorityId");
    }

    private ResultActions multipartAction(String issueId, String action, Session session, String json,
            MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/issues/" + issueId + "/" + action);
        request.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)));
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        request.header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()));
        return mvc.perform(request);
    }

    private ResultActions jsonAction(String issueId, String action, Session session, String json) throws Exception {
        return mvc.perform(post("/api/v1/issues/" + issueId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions plan(String issueId, Session authority) throws Exception {
        return multipartAction(issueId, "plan", authority, json("{\"version\":%d,\"actionTaken\":\"Send repair crew\","
                + "\"staffAllocated\":\"2 workers\",\"resourcesUsed\":\"Cold-mix asphalt\"}", version(issueId)));
    }

    private ResultActions resolve(String issueId, Session authority) throws Exception {
        return multipartAction(issueId, "resolve", authority,
                json("{\"version\":%d,\"remarks\":\"Repaired and inspected.\"}", version(issueId)),
                new MockMultipartFile("proofs", "fixed.jpg", "image/jpeg", TestFiles.jpeg(20, 20)));
    }

    private ResultActions reject(String issueId, Session authority) throws Exception {
        return multipartAction(issueId, "reject", authority,
                json("{\"version\":%d,\"reason\":\"Private property, not a public road.\"}", version(issueId)));
    }

    private ResultActions reopen(String issueId, Session citizen) throws Exception {
        return jsonAction(issueId, "reopen", citizen,
                json("{\"version\":%d,\"reason\":\"The problem is back after one day.\"}", version(issueId)));
    }

    @Test
    void fullLoopFromReportToResolutionAndReopen() throws Exception {
        Department dept = department(1);
        Session authority = dept.authorities().get(0);
        Session citizen = registerCitizen(uniqueEmail("loop"));
        String id = reportIssue(citizen, "Broken water valve cover", dept.category(), 18.51, 73.85);

        // Routed and assigned by the system, with a public explanation
        String routed = detail(id, authority);
        assertThat(JsonPath.<String>read(routed, "$.status")).isEqualTo("ASSIGNED");
        assertThat(JsonPath.<String>read(routed, "$.assignedAuthorityId")).isEqualTo(authority.userId());
        assertThat(JsonPath.<String>read(routed, "$.priorityReason")).isNotBlank();
        assertThat(JsonPath.<Boolean>read(routed, "$.flaggedForReview")).isFalse();
        mvc.perform(get("/api/v1/issues/assigned").header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken())))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)));

        plan(id, authority).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        multipartAction(id, "resolve", authority, json("{\"version\":%d,\"remarks\":\"Done.\"}", version(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROOF_REQUIRED"));
        resolve(id, authority)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        // Public timeline, with the resolution proof visible to everyone
        mvc.perform(get("/api/v1/issues/" + id + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", contains("REPORTED", "ASSIGNED", "PLANNED", "RESOLVED")))
                .andExpect(jsonPath("$[*].actor", contains("CITIZEN", "SYSTEM", "AUTHORITY", "AUTHORITY")))
                .andExpect(jsonPath("$[2].actionTaken").value("Send repair crew"))
                .andExpect(jsonPath("$[3].media[0].kind").value("RESOLUTION_PROOF"));

        reopen(id, citizen)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.assignedAuthorityId").doesNotExist());
        assertThat(assignee(id)).isEqualTo(authority.userId());

        mvc.perform(get("/api/v1/admin/audit").param("entityType", "ISSUE").param("entityId", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminSession().accessToken())))
                .andExpect(jsonPath("$.content[*].action",
                        hasItems("ISSUE_ROUTED", "ISSUE_PLANNED", "ISSUE_RESOLVED", "ISSUE_REOPENED")));
    }

    @Test
    void actingOnAStaleViewIsRejected() throws Exception {
        Department dept = department(1);
        String id = reportIssue(registerCitizen(uniqueEmail("stale")), "Loose manhole lid", dept.category(), 18.5, 73.8);
        long seen = version(id);
        plan(id, dept.authorities().get(0)).andExpect(status().isOk());

        multipartAction(id, "reject", dept.authorities().get(0),
                json("{\"version\":%d,\"reason\":\"Based on an old view of the issue.\"}", seen))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ISSUE"));
    }

    @Test
    void onlyAuthoritiesOfTheDepartmentCanAct() throws Exception {
        Department dept = department(2);
        String id = reportIssue(registerCitizen(uniqueEmail("who")), "Damaged bus shelter", dept.category(), 18.5, 73.8);
        String assigned = assignee(id);
        Session colleague = dept.authorities().stream()
                .filter(session -> !session.userId().equals(assigned)).findFirst().orElseThrow();
        Session otherDepartment = staffSession("AUTHORITY", departmentIdByCode("WATER"));

        plan(id, otherDepartment).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ALLOWED"));
        // Admins and citizens cannot act on issues at all
        plan(id, adminSession()).andExpect(status().isForbidden());
        plan(id, registerCitizen(uniqueEmail("nosy"))).andExpect(status().isForbidden());
        // ...and there is no endpoint to change routing, priority or deadline
        mvc.perform(patch("/api/v1/issues/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminSession().accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"LOW\",\"departmentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(JsonPath.<String>read(detail(id, null), "$.status")).isEqualTo("ASSIGNED");
        // A colleague in the same department may pick it up
        plan(id, colleague).andExpect(status().isOk());
    }

    @Test
    void issuesGoToTheLeastLoadedAuthority() throws Exception {
        Department dept = department(2);
        Session citizen = registerCitizen(uniqueEmail("load"));
        String first = reportIssue(citizen, "First broken bench", dept.category(), 18.5, 73.8);
        String second = reportIssue(citizen, "Second broken bench", dept.category(), 18.5, 73.8);

        assertThat(List.of(assignee(first), assignee(second)))
                .containsExactlyInAnyOrder(dept.authorities().get(0).userId(), dept.authorities().get(1).userId());
    }

    @Test
    void rejectionNeedsAReasonAndReopeningIsLimited() throws Exception {
        Department dept = department(1);
        Session authority = dept.authorities().get(0);
        Session citizen = registerCitizen(uniqueEmail("reject"));
        String id = reportIssue(citizen, "Hoarding blocking footpath", dept.category(), 18.5, 73.8);

        multipartAction(id, "reject", authority, json("{\"version\":%d,\"reason\":\"\"}", version(id)))
                .andExpect(status().isBadRequest());
        reject(id, authority).andExpect(jsonPath("$.status").value("REJECTED"));

        reopen(id, registerCitizen(uniqueEmail("stranger"))).andExpect(status().isForbidden());
        reopen(id, citizen).andExpect(status().isOk());
        reject(id, authority).andExpect(status().isOk());
        reopen(id, citizen).andExpect(status().isOk());
        reject(id, authority).andExpect(status().isOk());
        reopen(id, citizen)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REOPEN_LIMIT_REACHED"));
    }

    @Test
    void reopeningEndsAfterTheWindow() throws Exception {
        Department dept = department(1);
        Session citizen = registerCitizen(uniqueEmail("late"));
        String id = reportIssue(citizen, "Overgrown hedge", dept.category(), 18.5, 73.8);
        reject(id, dept.authorities().get(0)).andExpect(status().isOk());
        jdbc.update("UPDATE issues SET closed_at = now() - interval '30 days' WHERE id = ?", UUID.fromString(id));

        reopen(id, citizen).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REOPEN_WINDOW_CLOSED"));
    }

    @Test
    void returnedIssueIsReroutedElsewhereKeepingItsDeadline() throws Exception {
        Department dept = department(1);
        Session authority = dept.authorities().get(0);
        String id = reportIssue(registerCitizen(uniqueEmail("return")), "Dead dog on road", dept.category(), 18.5, 73.8);
        String deadline = JsonPath.read(detail(id, null), "$.slaDueAt");

        jsonAction(id, "return", authority, json("{\"version\":%d,\"reason\":\"Animal carcass removal is Public "
                + "Health's job.\"}", version(id)))
                .andExpect(status().isOk());

        String rerouted = detail(id, null);
        assertThat(JsonPath.<String>read(rerouted, "$.status")).isEqualTo("ASSIGNED");
        assertThat(JsonPath.<String>read(rerouted, "$.departmentName")).isEqualTo("General Administration");
        assertThat(JsonPath.<String>read(rerouted, "$.slaDueAt")).isEqualTo(deadline);
        mvc.perform(get("/api/v1/issues/" + id + "/history"))
                .andExpect(jsonPath("$[*].action", contains("REPORTED", "ASSIGNED", "RETURNED", "ASSIGNED")));
        mvc.perform(get("/api/v1/issues/assigned").header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
    }

    @Test
    void issuesWaitForAnAuthorityAndAreHandedOverWhenOneIsDisabled() throws Exception {
        Department dept = department(0);
        String id = reportIssue(registerCitizen(uniqueEmail("wait")), "Broken drinking fountain", dept.category(),
                18.5, 73.8);
        assertThat(JsonPath.<String>read(detail(id, null), "$.status")).isEqualTo("ASSIGNED");
        assertThat(assignee(id)).isNull();

        Session first = staffSession("AUTHORITY", dept.id());
        sweeper.assignWaitingIssues();
        assertThat(assignee(id)).isEqualTo(first.userId());

        Session second = staffSession("AUTHORITY", dept.id());
        jdbc.update("UPDATE users SET active = false WHERE id = ?", UUID.fromString(first.userId()));
        sweeper.moveIssuesFromDisabledAuthorities();
        assertThat(assignee(id)).isEqualTo(second.userId());

        String history = mvc.perform(get("/api/v1/issues/" + id + "/history"))
                .andReturn().getResponse().getContentAsString();
        List<String> actions = JsonPath.read(history, "$[*].action");
        List<String> actors = JsonPath.read(history, "$[*].actor");
        assertThat(actions.subList(actions.size() - 2, actions.size())).containsExactly("REASSIGNED", "REASSIGNED");
        assertThat(actors.get(actors.size() - 1)).isEqualTo("SYSTEM");
    }

    @Test
    void stuckReportsAreTriagedBySafetyNet() throws Exception {
        Department dept = department(1);
        String id = reportIssue(registerCitizen(uniqueEmail("stuck")), "Leaning lamp post", dept.category(), 18.5, 73.8);
        // Simulate a report whose triage was lost (e.g. the server restarted before it ran)
        UUID issueId = UUID.fromString(id);
        jdbc.update("DELETE FROM issue_assignments WHERE issue_id = ?", issueId);
        jdbc.update("UPDATE issues SET status = 'REPORTED', assigned_authority_id = NULL, "
                + "updated_at = now() - interval '1 hour' WHERE id = ?", issueId);

        sweeper.triageStuckIssues();

        assertThat(JsonPath.<String>read(detail(id, null), "$.status")).isEqualTo("ASSIGNED");
        assertThat(assignee(id)).isEqualTo(dept.authorities().get(0).userId());
    }

    @Test
    void historyCannotBeEdited() throws Exception {
        Department dept = department(1);
        String id = reportIssue(registerCitizen(uniqueEmail("tamper")), "Graffiti on wall", dept.category(), 18.5, 73.8);

        assertThatThrownBy(() -> jdbc.update("UPDATE issue_updates SET remarks = 'rewritten' WHERE issue_id = ?",
                UUID.fromString(id)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
