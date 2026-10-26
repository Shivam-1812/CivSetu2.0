package com.civsetu.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.civsetu.support.ApiTest;

class NotificationApiTest extends ApiTest {

    private record Case(Session reporter, Session authority, String issueId) {
    }

    /** A citizen reports in a fresh department that has one Authority. */
    private Case reported() throws Exception {
        UUID department = createDepartment();
        Session authority = staffSession("AUTHORITY", department);
        Session reporter = registerCitizen(uniqueEmail("notified"));
        String issueId = reportIssue(reporter, "Garbage dumped on the footpath", createCategory(department, "LOW"),
                18.5, 73.8);
        return new Case(reporter, authority, issueId);
    }

    private String notifications(Session user) throws Exception {
        return mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String ticket(Session user) throws Exception {
        String body = mvc.perform(post("/api/v1/notifications/stream-ticket")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ticket");
    }

    @Test
    void reporterAndAuthorityAreToldAboutTheirIssues() throws Exception {
        Case c = reported();

        // Newest first: routed, then received
        assertThat(JsonPath.<List<String>>read(notifications(c.reporter()), "$.content[*].type"))
                .containsExactly("ROUTED", "REPORT_RECEIVED");
        assertThat(JsonPath.<List<String>>read(notifications(c.authority()), "$.content[*].type"))
                .containsExactly("ASSIGNED_TO_YOU");

        planIssue(c.issueId(), c.authority());
        assertThat(JsonPath.<List<String>>read(notifications(c.reporter()), "$.content[*].type"))
                .contains("WORK_STARTED");
        // Nobody is told about their own action
        assertThat(JsonPath.<List<String>>read(notifications(c.authority()), "$.content[*].type"))
                .doesNotContain("WORK_STARTED");
    }

    @Test
    void commentsNotifyTheReporter() throws Exception {
        Case c = reported();
        mvc.perform(post("/api/v1/issues/" + c.issueId() + "/comments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerCitizen(uniqueEmail("c")).accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Same here\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, bearer(c.reporter().accessToken())))
                .andExpect(jsonPath("$.content[0].type").value("NEW_COMMENT"));
    }

    @Test
    void unreadCountAndMarkingAsRead() throws Exception {
        Case c = reported();
        String auth = bearer(c.reporter().accessToken());

        mvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.count").value(2));
        String firstId = JsonPath.read(notifications(c.reporter()), "$.content[0].id");
        mvc.perform(patch("/api/v1/notifications/" + firstId + "/read").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/v1/notifications/read-all").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/v1/notifications/unread-count").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.count").value(0));

        // Someone else's notification looks like a missing one
        mvc.perform(patch("/api/v1/notifications/" + firstId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(c.authority().accessToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void liveStreamPushesNewNotifications() throws Exception {
        Case c = reported();
        MvcResult stream = mvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket(c.reporter())))
                .andExpect(request().asyncStarted())
                .andReturn();

        planIssue(c.issueId(), c.authority());

        String events = stream.getResponse().getContentAsString();
        assertThat(events).contains(":connected");
        assertThat(events).contains("event:notification");
        assertThat(events).contains("\"type\":\"WORK_STARTED\"");
    }

    @Test
    void ticketsWorkOnceAndMissedEventsAreReplayed() throws Exception {
        Case c = reported();
        String ticket = ticket(c.reporter());
        mvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket)).andExpect(request().asyncStarted());

        mvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/stream")).andExpect(status().isUnauthorized());

        // Reconnecting with the id of the oldest notification replays the newer one
        String all = notifications(c.reporter());
        String oldest = JsonPath.read(all, "$.content[1].id");
        String newest = JsonPath.read(all, "$.content[0].id");
        MvcResult replay = mvc.perform(get("/api/v1/notifications/stream").param("ticket", ticket(c.reporter()))
                        .header("Last-Event-ID", oldest))
                .andExpect(request().asyncStarted())
                .andReturn();
        String events = replay.getResponse().getContentAsString();
        assertThat(events).contains("id:" + newest).doesNotContain("id:" + oldest);
    }

    @Test
    void notificationsNeedSignIn() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notifications/stream-ticket")).andExpect(status().isUnauthorized());
    }
}
