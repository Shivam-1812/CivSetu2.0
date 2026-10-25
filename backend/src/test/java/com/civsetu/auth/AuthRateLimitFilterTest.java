package com.civsetu.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(new AuthProperties.RateLimit(2, 1, 5));

    private record Outcome(boolean passedThrough, MockHttpServletResponse response) {
    }

    private Outcome send(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(chain.getRequest() != null, response);
    }

    @Test
    void blocksLoginAfterTheLimitWithProblemJson() throws Exception {
        assertThat(send("POST", "/api/v1/auth/login", "10.0.0.1").passedThrough()).isTrue();
        assertThat(send("POST", "/api/v1/auth/login", "10.0.0.1").passedThrough()).isTrue();

        Outcome blocked = send("POST", "/api/v1/auth/login", "10.0.0.1");
        assertThat(blocked.passedThrough()).isFalse();
        assertThat(blocked.response().getStatus()).isEqualTo(429);
        assertThat(blocked.response().getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.response().getContentType()).isEqualTo("application/problem+json");
        assertThat(blocked.response().getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    }

    @Test
    void limitsAreCountedPerClientAndPerEndpoint() throws Exception {
        assertThat(send("POST", "/api/v1/auth/register", "10.0.0.2").passedThrough()).isTrue();
        assertThat(send("POST", "/api/v1/auth/register", "10.0.0.2").passedThrough()).isFalse();

        // A different client, and a different endpoint for the same client, have their own budgets
        assertThat(send("POST", "/api/v1/auth/register", "10.0.0.3").passedThrough()).isTrue();
        assertThat(send("POST", "/api/v1/auth/login", "10.0.0.2").passedThrough()).isTrue();
    }

    @Test
    void otherRequestsAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(send("GET", "/api/v1/auth/me", "10.0.0.4").passedThrough()).isTrue();
            assertThat(send("POST", "/api/v1/users/me", "10.0.0.4").passedThrough()).isTrue();
        }
    }
}
