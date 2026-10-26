package com.civsetu.survey.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.civsetu.support.ApiTest;

class SurveyApiTest extends ApiTest {

    private record Area(Session authority, List<Session> reporters, Region region) {
    }

    /** Three citizens report issues within ~200 m of each other, in a department with one Authority. */
    private Area areaWithReports() throws Exception {
        UUID department = createDepartment();
        Session authority = staffSession("AUTHORITY", department);
        String category = createCategory(department, "MEDIUM");
        Region region = uniqueRegion();
        List<Session> reporters = List.of(registerCitizen(uniqueEmail("s1")), registerCitizen(uniqueEmail("s2")),
                registerCitizen(uniqueEmail("s3")));
        for (int i = 0; i < reporters.size(); i++) {
            reportIssue(reporters.get(i), "Broken streetlight " + i, category, region.lat(i), region.lng(0));
        }
        return new Area(authority, reporters, region);
    }

    private ResultActions createSurvey(Session staff, double latitude, double longitude) throws Exception {
        return mvc.perform(post("/api/v1/surveys")
                .header(HttpHeaders.AUTHORIZATION, bearer(staff.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("""
                        {"title":"Street lighting in your lane","centerLatitude":%.6f,"centerLongitude":%.6f,
                         "radiusMeters":500,"closesAt":"%s","questions":[
                           {"text":"How safe does the lane feel at night?","type":"RATING"},
                           {"text":"Should we add more lights?","type":"YES_NO"},
                           {"text":"Anything else?","type":"TEXT"}]}""",
                        latitude, longitude, Instant.now().plus(7, ChronoUnit.DAYS))));
    }

    private ResultActions answer(String surveyId, Session citizen, String json) throws Exception {
        return mvc.perform(post("/api/v1/surveys/" + surveyId + "/responses")
                .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String answers(String survey, int rating, boolean yes, String text) {
        List<String> ids = JsonPath.read(survey, "$.questions[*].id");
        return String.format(Locale.ROOT, "{\"answers\":[{\"questionId\":\"%s\",\"rating\":%d},"
                + "{\"questionId\":\"%s\",\"yesNo\":%b},{\"questionId\":\"%s\",\"text\":\"%s\"}]}",
                ids.get(0), rating, ids.get(1), yes, ids.get(2), text);
    }

    @Test
    void clusterSurveyIsCreatedAnsweredOnceAndSummarised() throws Exception {
        Area area = areaWithReports();

        String clusters = mvc.perform(get("/api/v1/surveys/clusters").param("radius", "500").param("minIssues", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(area.authority().accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(clusters, "$[*].issueCount")).containsExactly(3);
        double lat = JsonPath.<Double>read(clusters, "$[0].centerLatitude");
        double lng = JsonPath.<Double>read(clusters, "$[0].centerLongitude");

        String survey = createSurvey(area.authority(), lat, lng)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitedCount").value(3))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(survey, "$.id");

        // Invited people are notified and see it in their list
        Session first = area.reporters().get(0);
        mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(first.accessToken())))
                .andExpect(jsonPath("$.content[0].type").value("SURVEY_INVITATION"));
        mvc.perform(get("/api/v1/surveys/mine").header(HttpHeaders.AUTHORIZATION, bearer(first.accessToken())))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].answeredByMe").value(false));

        // Required answers are checked; text is optional
        List<String> questionIds = JsonPath.read(survey, "$.questions[*].id");
        answer(id, first, "{\"answers\":[{\"questionId\":\"" + questionIds.get(1) + "\",\"yesNo\":true}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ANSWER_REQUIRED"));
        answer(id, first, answers(survey, 2, true, "Very dark near the temple"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answeredByMe").value(true));
        answer(id, first, answers(survey, 5, false, "Changed my mind"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESPONDED"));
        answer(id, area.reporters().get(1), answers(survey, 4, true, ""))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/surveys/" + id + "/results")
                        .header(HttpHeaders.AUTHORIZATION, bearer(area.authority().accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitedCount").value(3))
                .andExpect(jsonPath("$.responseCount").value(2))
                .andExpect(jsonPath("$.questions[0].averageRating").value(3.0))
                .andExpect(jsonPath("$.questions[0].ratingCounts.2").value(1))
                .andExpect(jsonPath("$.questions[1].yes").value(2))
                .andExpect(jsonPath("$.questions[2].textAnswers[0]").value("Very dark near the temple"));

        // Closing stops further answers
        mvc.perform(post("/api/v1/surveys/" + id + "/close")
                        .header(HttpHeaders.AUTHORIZATION, bearer(area.authority().accessToken())))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        answer(id, area.reporters().get(2), answers(survey, 3, true, ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SURVEY_CLOSED"));
    }

    @Test
    void onlyInvitedPeopleOwnersAndAdminsSeeASurvey() throws Exception {
        Area area = areaWithReports();
        String survey = createSurvey(area.authority(), area.region().lat(1), area.region().lng(0))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(survey, "$.id");
        Session outsider = registerCitizen(uniqueEmail("outsider"));
        Session otherAuthority = staffSession("AUTHORITY", departmentIdByCode("WATER"));

        mvc.perform(get("/api/v1/surveys/" + id).header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
                .andExpect(status().isNotFound());
        answer(id, outsider, answers(survey, 3, true, "")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_INVITED"));
        mvc.perform(get("/api/v1/surveys/" + id + "/results")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherAuthority.accessToken())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/surveys/" + id + "/results")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminSession().accessToken())))
                .andExpect(status().isOk());
        // Citizens cannot create surveys
        createSurvey(outsider, area.region().lat(1), area.region().lng(0)).andExpect(status().isForbidden());
    }

    @Test
    void anAuthoritySurveysOnlyItsOwnDepartmentsReporters() throws Exception {
        Area area = areaWithReports();
        Session otherAuthority = staffSession("AUTHORITY", departmentIdByCode("WATER"));

        createSurvey(otherAuthority, area.region().lat(1), area.region().lng(0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_REPORTERS_IN_AREA"));
    }
}
