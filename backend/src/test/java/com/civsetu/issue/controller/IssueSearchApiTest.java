package com.civsetu.issue.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.civsetu.support.ApiTest;

/**
 * Each test places its issues in its own random {@link Region} and always filters by that
 * region's bounding box, so results only contain the test's own issues.
 */
class IssueSearchApiTest extends ApiTest {

    @Test
    void boundingBoxReturnsOnlyIssuesInsideIt() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("bbox"));
        Region here = uniqueRegion();
        Region elsewhere = uniqueRegion();
        String inside1 = reportIssue(citizen, "Inside one", "GARBAGE", here.lat(1), here.lng(1));
        String inside2 = reportIssue(citizen, "Inside two", "GARBAGE", here.lat(2), here.lng(2));
        reportIssue(citizen, "Outside", "GARBAGE", elsewhere.lat(1), elsewhere.lng(1));

        mvc.perform(get("/api/v1/issues").param("bbox", here.bbox()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(inside1, inside2)));
    }

    @Test
    void filtersByStatusCategoryPriorityAndText() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("filters"));
        Region region = uniqueRegion();
        reportIssue(citizen, "Overflowing garbage bin", "GARBAGE", region.lat(1), region.lng(1));
        reportIssue(citizen, "Exposed wire near school", "EXPOSED_WIRE", region.lat(2), region.lng(2));
        reportIssue(citizen, "Pothole by the temple", "POTHOLE", region.lat(3), region.lng(3));

        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("category", "EXPOSED_WIRE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Exposed wire near school"));

        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("priority", "CRITICAL"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].categoryCode").value("EXPOSED_WIRE"));

        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("priority", "CRITICAL", "MEDIUM"))
                .andExpect(jsonPath("$.totalElements").value(3));

        // Case-insensitive, matches title or description; % is treated literally
        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("q", "TEMPLE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].categoryCode").value("POTHOLE"));
        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("q", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Triage routes every report straight away, so none stays REPORTED
        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("status", "ASSIGNED"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("status", "REPORTED"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("status", "RESOLVED"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByDepartment() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("dept"));
        UUID department = createDepartment();
        String category = createCategory(department, "HIGH");
        String routed = reportIssue(citizen, "Routed to the test department", category, 10, 10);

        mvc.perform(get("/api/v1/issues").param("department", department.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(routed))
                .andExpect(jsonPath("$.content[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.content[0].departmentId").value(department.toString()));
    }

    @Test
    void filtersByCreationDate() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("dates"));
        Region region = uniqueRegion();
        reportIssue(citizen, "Recent issue", "GARBAGE", region.lat(1), region.lng(1));

        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("from", "2020-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("to", "2020-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/issues")
                        .param("from", "2026-10-02T00:00:00Z").param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void listIsPaginatedNewestFirstAndCappedAt50PerPage() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("pages"));
        Region region = uniqueRegion();
        reportIssue(citizen, "First", "GARBAGE", region.lat(1), region.lng(1));
        reportIssue(citizen, "Second", "GARBAGE", region.lat(2), region.lng(2));
        reportIssue(citizen, "Third", "GARBAGE", region.lat(3), region.lng(3));

        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].title").value("Third"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/issues").param("bbox", region.bbox()).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("First"));
        mvc.perform(get("/api/v1/issues").param("size", "500"))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    void mapReturnsSlimMarkersInsideTheViewport() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("map"));
        Region region = uniqueRegion();
        String id = reportIssue(citizen, "Waterlogged underpass", "WATERLOGGING", region.lat(1), region.lng(1));

        mvc.perform(get("/api/v1/issues/map").param("bbox", region.bbox()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].categoryCode").value("WATERLOGGING"))
                .andExpect(jsonPath("$.items[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.items[0].title").doesNotExist());
    }

    @Test
    void badSearchParametersReturn400() throws Exception {
        mvc.perform(get("/api/v1/issues/map"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BBOX_REQUIRED"));
        mvc.perform(get("/api/v1/issues/map").param("bbox", "73.9,18.6,73.8,18.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BBOX"));
        mvc.perform(get("/api/v1/issues").param("bbox", "1,2,3"))
                .andExpect(jsonPath("$.code").value("INVALID_BBOX"));
        mvc.perform(get("/api/v1/issues").param("category", "NOT_A_CATEGORY"))
                .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY"));
        mvc.perform(get("/api/v1/issues").param("status", "OPEN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/issues").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }
}
