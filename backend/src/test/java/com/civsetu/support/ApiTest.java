package com.civsetu.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

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

    /** Locale-independent formatting, so decimals are always written with a dot. */
    protected static String json(String template, Object... args) {
        return String.format(Locale.ROOT, template, args);
    }

    /**
     * A small random area somewhere on the globe. Issues placed here are invisible to other
     * tests' bounding-box queries, so tests can count results without interfering.
     */
    protected record Region(double latitude, double longitude) {

        /** The i-th point inside this region (about 100 m apart). */
        public double lat(int i) {
            return latitude + i * 0.001;
        }

        public double lng(int i) {
            return longitude + i * 0.001;
        }

        /** minLng,minLat,maxLng,maxLat covering every point of this region. */
        public String bbox() {
            return String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f",
                    longitude - 0.01, latitude - 0.01, longitude + 0.05, latitude + 0.05);
        }
    }

    protected static Region uniqueRegion() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new Region(-60 + random.nextDouble() * 120, -170 + random.nextDouble() * 340);
    }

    /** Reports an issue as the given citizen and returns its id. */
    protected String reportIssue(Session citizen, String title, String category, double latitude, double longitude)
            throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/issues")
                        .header(HttpHeaders.AUTHORIZATION, bearer(citizen.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"title\":\"%s\",\"description\":\"Details about %s\",\"category\":\"%s\","
                                + "\"latitude\":%.6f,\"longitude\":%.6f,\"address\":\"Test Street\"}",
                                title, title, category, latitude, longitude)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /** The issue's current version, needed by every workflow action. */
    protected long issueVersion(String issueId) throws Exception {
        String body = mvc.perform(get("/api/v1/issues/" + issueId)).andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$.version").longValue();
    }

    /** The assigned Authority starts work on the issue. */
    protected void planIssue(String issueId, Session authority) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/issues/" + issueId + "/plan");
        request.file(new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                json("{\"version\":%d,\"actionTaken\":\"Crew sent\"}", issueVersion(issueId))
                        .getBytes(StandardCharsets.UTF_8)));
        request.header(HttpHeaders.AUTHORIZATION, bearer(authority.accessToken()));
        mvc.perform(request).andExpect(status().isOk());
    }

    protected UUID departmentIdByCode(String code) {
        return jdbc.queryForObject("SELECT id FROM departments WHERE code = ?", UUID.class, code);
    }

    /** A new active category routed to the given department; returns its code. */
    protected String createCategory(UUID departmentId, String priority) {
        String code = "TEST_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO categories (code, label, default_department_id, default_priority) VALUES (?, ?, ?, ?)",
                code, "Test category " + code, departmentId, priority);
        return code;
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
                id, "TEST_" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT), "Test Department");
        return id;
    }

    private static Session session(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        return new Session(JsonPath.read(body, "$.accessToken"), cookie, JsonPath.read(body, "$.user.id"));
    }
}
