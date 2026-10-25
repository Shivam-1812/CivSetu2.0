package com.civsetu.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Helpers for API tests: sign-up, login and seeded staff accounts. Every helper uses a fresh,
 * unique email, so tests never depend on each other's data.
 */
public abstract class ApiTest extends IntegrationTest {

    protected static final String PASSWORD = "correct-horse-42";
    protected static final String REFRESH_COOKIE = "civsetu_refresh";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Tokens from a successful login or registration. */
    protected record Session(String accessToken, Cookie refreshCookie, String userId) {
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.test";
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    protected static String json(String template, Object... args) {
        return String.format(template, args);
    }

    protected Session registerCitizen(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"name\":\"Test Citizen\",\"email\":\"%s\",\"password\":\"%s\"}",
                                email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return session(result);
    }

    protected Session login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return session(result);
    }

    /** Inserts an account directly (staff cannot sign up) and logs it in. */
    protected Session staffSession(String role, UUID departmentId) throws Exception {
        String email = uniqueEmail(role.toLowerCase());
        jdbc.update("INSERT INTO users (name, email, password_hash, role, department_id) VALUES (?, ?, ?, ?, ?)",
                "Test " + role, email, passwordEncoder.encode(PASSWORD), role, departmentId);
        return login(email, PASSWORD);
    }

    protected Session adminSession() throws Exception {
        return staffSession("ADMIN", null);
    }

    protected UUID createDepartment() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO departments (id, code, name) VALUES (?, ?, ?)",
                id, "TEST-" + id.toString().substring(0, 8), "Test Department");
        return id;
    }

    private static Session session(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        return new Session(JsonPath.read(body, "$.accessToken"), cookie, JsonPath.read(body, "$.user.id"));
    }
}
