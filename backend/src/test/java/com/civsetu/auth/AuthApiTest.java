package com.civsetu.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import com.civsetu.ApiTest;

class AuthApiTest extends ApiTest {

    @Autowired
    JwtEncoder jwtEncoder;

    @Test
    void registerThenLoginThenMe() throws Exception {
        String email = uniqueEmail("asha");
        Session registered = registerCitizen(email);
        assertThat(registered.accessToken()).isNotBlank();

        Session session = login(email, PASSWORD);

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CITIZEN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginSetsSecureRefreshCookieAndReturnsBearerToken() throws Exception {
        String email = uniqueEmail("cookie");
        registerCitizen(email);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(result -> {
                    MockCookie cookie = (MockCookie) result.getResponse().getCookie(REFRESH_COOKIE);
                    assertThat(cookie).isNotNull();
                    assertThat(cookie.isHttpOnly()).isTrue();
                    assertThat(cookie.getSameSite()).isEqualTo("Strict");
                    assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
                });
    }

    @Test
    void signupCannotChooseItsOwnRole() throws Exception {
        String email = uniqueEmail("sneaky");
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"name\":\"Sneaky\",\"email\":\"%s\",\"password\":\"%s\","
                                + "\"role\":\"ADMIN\",\"departmentId\":\"%s\"}", email, PASSWORD, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CITIZEN"))
                .andExpect(jsonPath("$.user.departmentId").doesNotExist());
    }

    @Test
    void emailIsUniqueIgnoringCase() throws Exception {
        String email = uniqueEmail("dup");
        registerCitizen(email);

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"name\":\"Dup\",\"email\":\"%s\",\"password\":\"%s\"}",
                                email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void invalidRegistrationListsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"not-an-email\",\"password\":\"short\",\"phone\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameError() throws Exception {
        String email = uniqueEmail("ravi");
        registerCitizen(email);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"%s\",\"password\":\"wrong-password\"}", email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", uniqueEmail("nobody"), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        String email = uniqueEmail("disabled");
        registerCitizen(email);
        jdbc.update("UPDATE users SET active = false WHERE email = ?", email);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{\"email\":\"%s\",\"password\":\"%s\"}", email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void refreshRotatesTheTokenAndReuseEndsTheSession() throws Exception {
        Session session = registerCitizen(uniqueEmail("rotate"));
        Cookie first = session.refreshCookie();

        MvcResult rotated = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = rotated.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the old token signals theft: rejected, and the whole session is revoked
        mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        Session session = registerCitizen(uniqueEmail("logout"));

        mvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getCookie(REFRESH_COOKIE).getMaxAge()).isZero());

        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_MISSING"));
    }

    @Test
    void expiredAccessTokenReturns401() throws Exception {
        Session session = registerCitizen(uniqueEmail("expired"));
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("civsetu")
                .subject(session.userId())
                .issuedAt(past)
                .expiresAt(past.plus(15, ChronoUnit.MINUTES))
                .claim("role", "CITIZEN")
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void tamperedAccessTokenReturns401() throws Exception {
        Session session = registerCitizen(uniqueEmail("tamper"));
        String token = session.accessToken();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void citizenCanEditOwnProfileButNotEmailOrRole() throws Exception {
        String email = uniqueEmail("profile");
        Session session = registerCitizen(email);

        mvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Asha Patil \",\"phone\":\"+919876543210\","
                                + "\"email\":\"hacker@example.test\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha Patil"))
                .andExpect(jsonPath("$.phone").value("+919876543210"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CITIZEN"));
    }
}
