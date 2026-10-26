package com.civsetu.department.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.civsetu.support.ApiTest;

class RoutingApiTest extends ApiTest {

    private static String uniqueCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    @Test
    void anyoneCanReadActiveCategoriesAndDepartments() throws Exception {
        String roadsId = departmentIdByCode("ROADS").toString();

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'POTHOLE')].departmentId").value(hasItem(roadsId)))
                .andExpect(jsonPath("$[?(@.code == 'EXPOSED_WIRE')].defaultPriority").value(hasItem("CRITICAL")));
        mvc.perform(get("/api/v1/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code").value(hasItem("SANITATION")));
    }

    @Test
    void adminCreatesDepartmentAndCategoryThatRoutesNewIssues() throws Exception {
        Session admin = adminSession();
        String departmentCode = uniqueCode("METRO");
        String categoryCode = uniqueCode("METRO_GATE");

        String departmentJson = mvc.perform(post("/api/v1/admin/departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"code\":\"%s\",\"name\":\"Metro Works\",\"area\":\"Zone 4\"}",
                                departmentCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String departmentId = JsonPath.read(departmentJson, "$.id");

        mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"code\":\"%s\",\"label\":\"Metro gate blocked\",\"departmentId\":\"%s\","
                                + "\"defaultPriority\":\"HIGH\"}", categoryCode, departmentId)))
                .andExpect(status().isCreated());

        Session citizen = registerCitizen(uniqueEmail("metro"));
        String issueId = reportIssue(citizen, "Gate blocked at station", categoryCode, 18.5, 73.8);
        mvc.perform(get("/api/v1/issues/" + issueId))
                .andExpect(jsonPath("$.departmentId").value(departmentId))
                .andExpect(jsonPath("$.departmentName").value("Metro Works"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    void deactivatedCategoryLeavesThePublicList() throws Exception {
        Session admin = adminSession();
        String code = createCategory(departmentIdByCode("PARKS"), "LOW");
        UUID id = jdbc.queryForObject("SELECT id FROM categories WHERE code = ?", UUID.class, code);

        mvc.perform(patch("/api/v1/admin/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$[*].code").value(not(hasItem(code))));
        mvc.perform(get("/api/v1/admin/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(jsonPath("$[*].code").value(hasItem(code)));
    }

    @Test
    void departmentWithActiveCategoriesCannotBeDeactivated() throws Exception {
        Session admin = adminSession();
        UUID department = createDepartment();
        createCategory(department, "LOW");

        mvc.perform(patch("/api/v1/admin/departments/" + department)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_IN_USE"));
    }

    @Test
    void codesAreValidatedAndUnique() throws Exception {
        Session admin = adminSession();

        mvc.perform(post("/api/v1/admin/departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ROADS\",\"name\":\"Second roads\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_TAKEN"));

        mvc.perform(post("/api/v1/admin/departments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad code\",\"name\":\"Bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'code')]").exists());

        mvc.perform(post("/api/v1/admin/categories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"code\":\"%s\",\"label\":\"x\",\"departmentId\":\"%s\","
                                + "\"defaultPriority\":\"LOW\"}", uniqueCode("ORPHAN"), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void onlyAdminsManageRouting() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("nosy"));
        Session authority = staffSession("AUTHORITY", departmentIdByCode("ROADS"));

        for (Session session : new Session[] {citizen, authority}) {
            mvc.perform(post("/api/v1/admin/departments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"HACKED\",\"name\":\"Hacked\"}"))
                    .andExpect(status().isForbidden());
        }
    }
}
