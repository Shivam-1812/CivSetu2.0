package com.civsetu.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.civsetu.support.ApiTest;
import com.civsetu.support.TestFiles;

/**
 * A fresh department with a known set of issues, so every number can be checked exactly:
 * three reports at one spot, one resolved on time, one overdue, one open.
 */
class AnalyticsApiTest extends ApiTest {

    private record Fixture(UUID department, String departmentCode, Session authority, Session reporter,
            String overdueId) {
    }

    private Fixture fixture() throws Exception {
        UUID department = createDepartment();
        String code = jdbc.queryForObject("SELECT code FROM departments WHERE id = ?", String.class, department);
        Session authority = staffSession("AUTHORITY", department);
        String category = createCategory(department, "MEDIUM");
        Session reporter = registerCitizen(uniqueEmail("stats"));
        Region spot = uniqueRegion();

        String resolved = reportIssue(reporter, "Resolved one", category, spot.lat(0), spot.lng(0));
        String overdue = reportIssue(reporter, "Overdue one", category, spot.lat(0), spot.lng(0));
        reportIssue(registerCitizen(uniqueEmail("other")), "Open one", category, spot.lat(0), spot.lng(0));

        planIssue(resolved, authority);
        MockMultipartHttpServletRequestBuilder resolve = multipart("/api/v1/issues/" + resolved + "/resolve");
        resolve.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json("{\"version\":%d,\"remarks\":\"Fixed\"}", issueVersion(resolved))
                        .getBytes(StandardCharsets.UTF_8)));
        resolve.file(new MockMultipartFile("proofs", "p.jpg", "image/jpeg", TestFiles.jpeg(10, 10)));
        resolve.header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken()));
        mvc.perform(resolve).andExpect(status().isOk());

        jdbc.update("UPDATE issues SET sla_due_at = now() - interval '3 hours' WHERE id = ?", UUID.fromString(overdue));
        return new Fixture(department, code, authority, reporter, overdue);
    }

    private String fetch(String url, Session session) throws Exception {
        return mvc.perform(get(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void authoritySeesItsDepartmentsFigures() throws Exception {
        Fixture f = fixture();
        String summary = fetch("/api/v1/analytics/summary", f.authority());

        assertThat(JsonPath.<String>read(summary, "$.scope")).isEqualTo("DEPARTMENT");
        assertThat(JsonPath.<Integer>read(summary, "$.reported")).isEqualTo(3);
        assertThat(JsonPath.<Map<String, Integer>>read(summary, "$.byStatus"))
                .containsEntry("RESOLVED", 1).containsEntry("ASSIGNED", 2);
        assertThat(JsonPath.<List<String>>read(summary, "$.byDepartment[*].key")).containsExactly(f.departmentCode());
        assertThat(JsonPath.<Integer>read(summary, "$.resolved")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(summary, "$.resolvedWithinDeadline")).isEqualTo(1);
        assertThat(JsonPath.<Double>read(summary, "$.slaCompliancePercent")).isEqualTo(100.0);
        assertThat(JsonPath.<Integer>read(summary, "$.openNow")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(summary, "$.openOverdueNow")).isEqualTo(1);

        String trend = fetch("/api/v1/analytics/trend?days=7", f.authority());
        assertThat(JsonPath.<List<Object>>read(trend, "$[*]")).hasSize(7);
        assertThat(JsonPath.<Integer>read(trend, "$[6].reported")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(trend, "$[6].resolved")).isEqualTo(1);

        String hotspots = fetch("/api/v1/analytics/hotspots", f.authority());
        assertThat(JsonPath.<Integer>read(hotspots, "$[0].reported")).isEqualTo(3);
        assertThat(JsonPath.<Integer>read(hotspots, "$[0].stillOpen")).isEqualTo(2);

        String overdue = fetch("/api/v1/analytics/overdue", f.authority());
        assertThat(JsonPath.<List<String>>read(overdue, "$.content[*].id")).containsExactly(f.overdueId());
        assertThat(JsonPath.<Double>read(overdue, "$.content[0].hoursOverdue")).isGreaterThan(2.5);
    }

    @Test
    void citizensSeeOnlyTheirOwnReportsAndAdminsSeeTheCity() throws Exception {
        Fixture f = fixture();

        String mine = fetch("/api/v1/analytics/summary", f.reporter());
        assertThat(JsonPath.<String>read(mine, "$.scope")).isEqualTo("MY_REPORTS");
        assertThat(JsonPath.<Integer>read(mine, "$.reported")).isEqualTo(2);

        String city = fetch("/api/v1/analytics/summary", adminSession());
        assertThat(JsonPath.<String>read(city, "$.scope")).isEqualTo("CITY");
        assertThat(JsonPath.<Integer>read(city, "$.reported")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void periodsAreValidatedAndSignInIsRequired() throws Exception {
        Session admin = adminSession();
        mvc.perform(get("/api/v1/analytics/summary").param("from", "2026-10-02T00:00:00Z")
                        .param("to", "2026-10-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        mvc.perform(get("/api/v1/analytics/summary")).andExpect(status().isUnauthorized());
    }
}
