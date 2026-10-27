package com.civsetu.engagement.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.civsetu.support.ApiTest;

class EngagementApiTest extends ApiTest {

    private ResultActions vote(String issueId, Session voter, String value) throws Exception {
        return mvc.perform(put("/api/v1/issues/" + issueId + "/vote")
                .header(HttpHeaders.AUTHORIZATION, bearer(voter.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"" + value + "\"}"));
    }

    private ResultActions comment(String issueId, Session author, String body) throws Exception {
        return mvc.perform(post("/api/v1/issues/" + issueId + "/comments")
                .header(HttpHeaders.AUTHORIZATION, bearer(author.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("{\"body\":\"%s\"}", body)));
    }

    private String newIssue() throws Exception {
        return reportIssue(registerCitizen(uniqueEmail("reporter")), "Overflowing bin near market", "GARBAGE",
                18.5, 73.8);
    }

    @Test
    void oneVotePerCitizenThatCanBeChangedOrRemoved() throws Exception {
        String id = newIssue();
        Session neighbour = registerCitizen(uniqueEmail("neighbour"));

        vote(id, neighbour, "AGREE").andExpect(status().isOk())
                .andExpect(jsonPath("$.agree").value(1)).andExpect(jsonPath("$.myVote").value("AGREE"));
        // Repeating the same vote changes nothing
        vote(id, neighbour, "AGREE").andExpect(jsonPath("$.agree").value(1));
        vote(id, neighbour, "DISAGREE")
                .andExpect(jsonPath("$.agree").value(0)).andExpect(jsonPath("$.disagree").value(1));
        mvc.perform(delete("/api/v1/issues/" + id + "/vote")
                        .header(HttpHeaders.AUTHORIZATION, bearer(neighbour.accessToken())))
                .andExpect(jsonPath("$.disagree").value(0))
                .andExpect(jsonPath("$.myVote").doesNotExist());

        // Counts are public, and part of the issue detail
        vote(id, neighbour, "AGREE");
        mvc.perform(get("/api/v1/issues/" + id + "/votes")).andExpect(jsonPath("$.agree").value(1));
        mvc.perform(get("/api/v1/issues/" + id)).andExpect(jsonPath("$.votes.agree").value(1));
    }

    @Test
    void listRowsCarryCountsAndTheViewersOwnVote() throws Exception {
        String title = "Feed card " + UUID.randomUUID().toString().substring(0, 8);
        String id = reportIssue(registerCitizen(uniqueEmail("feed")), title, "GARBAGE", 18.5, 73.8);
        Session neighbour = registerCitizen(uniqueEmail("liker"));
        vote(id, neighbour, "AGREE");
        comment(id, neighbour, "Seen it too");

        mvc.perform(get("/api/v1/issues").param("q", title)
                        .header(HttpHeaders.AUTHORIZATION, bearer(neighbour.accessToken())))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].description").isNotEmpty())
                .andExpect(jsonPath("$.content[0].agreeCount").value(1))
                .andExpect(jsonPath("$.content[0].disagreeCount").value(0))
                .andExpect(jsonPath("$.content[0].commentCount").value(1))
                .andExpect(jsonPath("$.content[0].myVote").value("AGREE"))
                .andExpect(jsonPath("$.content[0].imageCount").value(0))
                .andExpect(jsonPath("$.content[0].coverImageUrl").doesNotExist());
        // Anonymous viewers get the same counts without a vote of their own
        mvc.perform(get("/api/v1/issues").param("q", title))
                .andExpect(jsonPath("$.content[0].agreeCount").value(1))
                .andExpect(jsonPath("$.content[0].myVote").doesNotExist());
    }

    @Test
    void onlyOtherCitizensCanVote() throws Exception {
        Session reporter = registerCitizen(uniqueEmail("self"));
        String id = reportIssue(reporter, "Broken bench", "PARK_MAINTENANCE", 18.5, 73.8);

        vote(id, reporter, "AGREE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWN_ISSUE"));
        vote(id, adminSession(), "AGREE").andExpect(status().isForbidden());
        vote(id, staffSession("AUTHORITY", departmentIdByCode("PARKS")), "AGREE").andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/issues/" + id + "/vote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"AGREE\"}"))
                .andExpect(status().isUnauthorized());
        vote(UUID.randomUUID().toString(), registerCitizen(uniqueEmail("ghost")), "AGREE")
                .andExpect(status().isNotFound());
    }

    @Test
    void commentsArePublicAndLimitedPerPerson() throws Exception {
        String id = newIssue();
        Session talker = registerCitizen(uniqueEmail("talker"));
        Session official = staffSession("AUTHORITY", departmentIdByCode("SANITATION"));

        comment(id, talker, "Same problem on my street").andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Test Citizen"));
        comment(id, official, "Collection truck scheduled for tomorrow").andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("AUTHORITY"));
        comment(id, talker, "Second comment").andExpect(status().isCreated());
        comment(id, talker, "Third comment").andExpect(status().isCreated());
        comment(id, talker, "Fourth comment")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMENT_LIMIT_REACHED"));

        mvc.perform(get("/api/v1/issues/" + id + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].body").value("Same problem on my street"));
        mvc.perform(get("/api/v1/issues/" + id)).andExpect(jsonPath("$.commentCount").value(4));
        comment(id, talker, " ").andExpect(status().isBadRequest());
    }

    @Test
    void adminCanHideACommentWithAPublicReason() throws Exception {
        String id = newIssue();
        String body = comment(id, registerCitizen(uniqueEmail("rude")), "Something abusive")
                .andReturn().getResponse().getContentAsString();
        String commentId = JsonPath.read(body, "$.id");
        Session admin = adminSession();

        mvc.perform(patch("/api/v1/admin/comments/" + commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        mvc.perform(patch("/api/v1/admin/comments/" + commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":true,\"reason\":\"Abusive language\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/issues/" + id + "/comments"))
                .andExpect(jsonPath("$.content[0].hidden").value(true))
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.content[0].hiddenReason").value("Abusive language"));
        mvc.perform(get("/api/v1/admin/audit").param("entityType", "COMMENT").param("entityId", commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(jsonPath("$.content[*].action", hasItem("COMMENT_HIDDEN")));

        mvc.perform(patch("/api/v1/admin/comments/" + commentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerCitizen(uniqueEmail("c")).accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidden\":false}"))
                .andExpect(status().isForbidden());
    }
}
