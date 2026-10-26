package com.civsetu.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.civsetu.support.ApiTest;
import com.civsetu.user.service.UserService;

class AdminUserApiTest extends ApiTest {

    @Autowired
    UserService users;

    private static String staffJson(String email, String role, UUID departmentId) {
        String department = departmentId == null ? "null" : "\"" + departmentId + "\"";
        return String.format("{\"name\":\"Staff Member\",\"email\":\"%s\",\"password\":\"%s\",\"role\":\"%s\","
                + "\"departmentId\":%s}", email, PASSWORD, role, department);
    }

    @Test
    void citizenCannotReachAdminRoutes() throws Exception {
        Session citizen = registerCitizen(uniqueEmail("citizen"));

        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson(uniqueEmail("x"), "ADMIN", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorityCannotReachAdminRoutes() throws Exception {
        Session authority = staffSession("AUTHORITY", createDepartment());

        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallerGets401NotForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void adminCreatesAuthorityWhoCanLogInWithDepartmentClaim() throws Exception {
        Session admin = adminSession();
        UUID department = createDepartment();
        String email = uniqueEmail("officer");

        mvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson(email, "AUTHORITY", department)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("AUTHORITY"))
                .andExpect(jsonPath("$.departmentId").value(department.toString()));

        Session authority = login(email, PASSWORD);
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AUTHORITY"));
    }

    @Test
    void authorityNeedsAnExistingDepartment() throws Exception {
        Session admin = adminSession();

        mvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson(uniqueEmail("nodept"), "AUTHORITY", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_REQUIRED"));

        mvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson(uniqueEmail("baddept"), "AUTHORITY", UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void adminCannotCreateCitizensOrDepartmentAdmins() throws Exception {
        Session admin = adminSession();

        mvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson(uniqueEmail("cit"), "CITIZEN", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));

        mvc.perform(post("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffJson(uniqueEmail("adm"), "ADMIN", createDepartment())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_ALLOWED"));
    }

    @Test
    void adminListsUsersByRoleWithPageSizeCappedAt50() throws Exception {
        Session admin = adminSession();

        mvc.perform(get("/api/v1/admin/users")
                        .param("role", "ADMIN")
                        .param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
    }

    @Test
    void bootstrapAdminIsCreatedOnlyWhenNoAdminExists() throws Exception {
        // Simulate a fresh installation: demote existing Admins (people with audit history cannot be deleted)
        jdbc.update("UPDATE users SET role = 'CITIZEN' WHERE role = 'ADMIN'");
        String email = uniqueEmail("first-admin");

        assertThat(users.ensureBootstrapAdmin("First Admin", email, PASSWORD)).isTrue();
        assertThat(users.ensureBootstrapAdmin("Second Admin", uniqueEmail("second-admin"), PASSWORD)).isFalse();

        Session admin = login(email, PASSWORD);
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }
}
