package com.civsetu.issue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.civsetu.support.ApiTest;

class DuplicateApiTest extends ApiTest {

    private record Setup(String category, Session authority, Region region) {
    }

    private Setup setup() throws Exception {
        UUID department = createDepartment();
        return new Setup(createCategory(department, "MEDIUM"), staffSession("AUTHORITY", department), uniqueRegion());
    }

    private String report(Setup setup, String title, double latitude, double longitude) throws Exception {
        return mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerCitizen(uniqueEmail("dup")).accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"title\":\"%s\",\"description\":\"Reported by a resident nearby.\","
                                + "\"category\":\"%s\",\"latitude\":%.6f,\"longitude\":%.6f,\"address\":\"Lane 4\"}",
                                title, setup.category(), latitude, longitude)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions markDuplicate(String issueId, Session authority, String originalId) throws Exception {
        return mvc.perform(post("/api/v1/issues/" + issueId + "/duplicate")
                .header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"version\":%d,\"duplicateOfId\":\"%s\"}", issueVersion(issueId), originalId)));
    }

    @Test
    void newReportListsSimilarOpenIssuesNearby() throws Exception {
        Setup setup = setup();
        Region at = setup.region();
        String first = JsonPath.read(report(setup, "Huge pothole near the bus stop", at.lat(0), at.lng(0)), "$.id");

        // About 100 m away, similar words
        String second = report(setup, "Big pothole at bus stop", at.lat(0) + 0.0009, at.lng(0));
        assertThat(JsonPath.<List<String>>read(second, "$.possibleDuplicates[*].issue.id")).containsExactly(first);
        assertThat(JsonPath.<Integer>read(second, "$.possibleDuplicates[0].distanceMeters")).isBetween(80, 120);
        assertThat(JsonPath.<Double>read(second, "$.possibleDuplicates[0].titleSimilarity")).isGreaterThan(0.2);

        // Far away (about 1 km): not a candidate
        String far = report(setup, "Huge pothole near the bus stop", at.lat(0) + 0.009, at.lng(0));
        assertThat(JsonPath.<List<String>>read(far, "$.possibleDuplicates[*].issue.id")).doesNotContain(first);

        // The same check is available before reporting
        mvc.perform(get("/api/v1/issues/similar")
                        .param("latitude", String.valueOf(at.lat(0)))
                        .param("longitude", String.valueOf(at.lng(0)))
                        .param("category", setup.category())
                        .param("title", "pothole bus stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].issue.id", hasItem(first)));
        mvc.perform(get("/api/v1/issues/" + first + "/similar"))
                .andExpect(jsonPath("$[*].issue.id", not(hasItem(first))));
    }

    @Test
    void assignedAuthorityCanCloseAsDuplicateAndReporterCanReopen() throws Exception {
        Setup setup = setup();
        Region at = setup.region();
        String original = JsonPath.read(report(setup, "Streetlight dark on lane 4", at.lat(0), at.lng(0)), "$.id");
        Session reporter = registerCitizen(uniqueEmail("second"));
        String copy = reportIssue(reporter, "Lane 4 light not working", setup.category(), at.lat(1), at.lng(1));

        markDuplicate(copy, setup.authority(), copy)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORIGINAL"));
        markDuplicate(copy, setup.authority(), original)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.duplicateOfId").value(original));

        mvc.perform(get("/api/v1/issues/" + copy + "/history"))
                .andExpect(jsonPath("$[-1:].action").value(hasItem("MARKED_DUPLICATE")));

        mvc.perform(post("/api/v1/issues/" + copy + "/reopen")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"version\":%d,\"reason\":\"The other report is about a different pole.\"}",
                                issueVersion(copy))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }
}
