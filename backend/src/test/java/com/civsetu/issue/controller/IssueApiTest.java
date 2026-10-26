package com.civsetu.issue.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.civsetu.support.ApiTest;

class IssueApiTest extends ApiTest {

    private static final String POTHOLE_REPORT = "{\"title\":\"Deep pothole on FC Road\","
            + "\"description\":\"Large pothole near the bus stop, two-wheelers are slipping.\","
            + "\"category\":\"POTHOLE\",\"latitude\":18.5236,\"longitude\":73.8478,"
            + "\"address\":\"FC Road, Shivaji Nagar, Pune\"}";

    @Test
    void citizenReportIsStoredAndRoutedByCategory() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("reporter"));

        MvcResult result = mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POTHOLE_REPORT))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/v1/issues/" + id);

        mvc.perform(get("/api/v1/issues/" + id).header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken())))
                .andExpect(status().isOk())
                // Routed automatically right after the report was saved
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.priorityReason").value("Category 'Pothole or damaged road' is medium priority."))
                .andExpect(jsonPath("$.categoryCode").value("POTHOLE"))
                .andExpect(jsonPath("$.departmentName").value("Roads and Infrastructure"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.prioritySource").value("RULE"))
                .andExpect(jsonPath("$.latitude").value(closeTo(18.5236, 1e-9)))
                .andExpect(jsonPath("$.longitude").value(closeTo(73.8478, 1e-9)))
                .andExpect(jsonPath("$.reportedByMe").value(true))
                .andExpect(jsonPath("$.reporter.id").value(citizen.userId()));

        // MEDIUM priority: due 4 days (96 hours) after reporting
        Instant createdAt = Instant.parse(JsonPath.read(body, "$.createdAt"));
        Instant slaDueAt = Instant.parse(JsonPath.read(body, "$.slaDueAt"));
        assertThat(Duration.between(createdAt, slaDueAt)).isCloseTo(Duration.ofHours(96), Duration.ofSeconds(5));
    }

    @Test
    void serverControlledFieldsInTheRequestAreIgnored() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("sneaky"));
        String report = POTHOLE_REPORT.replace("}", ",\"reporterId\":\"" + UUID.randomUUID() + "\","
                + "\"status\":\"RESOLVED\",\"priority\":\"CRITICAL\",\"departmentId\":\"" + UUID.randomUUID() + "\"}");

        mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporter.id").value(citizen.userId()))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.departmentName").value("Roads and Infrastructure"));
    }

    @Test
    void invalidCoordinatesAreRejected() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("gps"));
        String report = POTHOLE_REPORT.replace("18.5236", "91.5").replace("73.8478", "-181");

        mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'latitude')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'longitude')]").exists());
    }

    @Test
    void missingFieldsAreListed() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("empty"));

        mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Hole\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'description')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'category')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'latitude')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'address')]").exists());
    }

    @Test
    void unknownOrInactiveCategoryIsRejected() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("cat"));

        mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POTHOLE_REPORT.replace("POTHOLE", "NOT_A_CATEGORY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY"));

        String inactive = createCategory(departmentIdByCode("ROADS"), "LOW");
        jdbc.update("UPDATE categories SET active = false WHERE code = ?", inactive);
        mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POTHOLE_REPORT.replace("POTHOLE", inactive)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY"));
    }

    @Test
    void onlyCitizensCanReport() throws Exception {
        Session authority = staffSession("AUTHORITY", departmentIdByCode("ROADS"));
        Session admin = adminSession();

        for (Session staff : new Session[] {authority, admin}) {
            mvc.perform(post("/api/v1/issues")
                            .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(POTHOLE_REPORT))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/issues").contentType(MediaType.APPLICATION_JSON).content(POTHOLE_REPORT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reporterContactIsVisibleOnlyToReporterAndResponsibleStaff() throws Exception {
        Session reporter = registerCitizen(uniqueEmail("owner"));
        String id = reportIssue(reporter, "Streetlight out", "POTHOLE", 18.52, 73.85);
        String url = "/api/v1/issues/" + id;

        // Public: anonymous visitors and other citizens
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Streetlight out"))
                .andExpect(jsonPath("$.reporter").doesNotExist())
                .andExpect(jsonPath("$.reportedByMe").value(false));
        Session neighbour = registerCitizen(uniqueEmail("neighbour"));
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(neighbour.accessToken())))
                .andExpect(jsonPath("$.reporter").doesNotExist());

        // An Authority of another department sees only public fields
        Session otherAuthority = staffSession("AUTHORITY", departmentIdByCode("WATER"));
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(otherAuthority.accessToken())))
                .andExpect(jsonPath("$.reporter").doesNotExist());

        // The responsible department's Authority and Admins see the reporter
        Session roadsAuthority = staffSession("AUTHORITY", departmentIdByCode("ROADS"));
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(roadsAuthority.accessToken())))
                .andExpect(jsonPath("$.reporter.id").value(reporter.userId()))
                .andExpect(jsonPath("$.reporter.email").exists());
        Session admin = adminSession();
        mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(jsonPath("$.reporter.id").value(reporter.userId()));
    }

    @Test
    void unknownIssueReturns404() throws Exception {
        mvc.perform(get("/api/v1/issues/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/issues/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mineListsOnlyTheCallersReports() throws Exception {
        Session asha = registerCitizen(uniqueEmail("asha"));
        Session ravi = registerCitizen(uniqueEmail("ravi"));
        reportIssue(asha, "Asha first report", "GARBAGE", 18.50, 73.80);
        reportIssue(asha, "Asha second report", "GARBAGE", 18.51, 73.81);
        reportIssue(ravi, "Ravi report", "GARBAGE", 18.52, 73.82);

        mvc.perform(get("/api/v1/issues/mine").header(HttpHeaders.AUTHORIZATION, bearer(asha.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Asha second report"))
                .andExpect(jsonPath("$.content[1].title").value("Asha first report"));

        mvc.perform(get("/api/v1/issues/mine")).andExpect(status().isUnauthorized());
    }
}
