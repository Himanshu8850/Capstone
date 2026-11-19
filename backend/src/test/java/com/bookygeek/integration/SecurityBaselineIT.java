package com.bookygeek.integration;

import com.bookygeek.common.security.JwtService;
import com.bookygeek.common.security.SecurityProperties;
import com.bookygeek.common.security.TokenVersionLookup;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security baseline of the approved contract (T043/T044):
 * <ul>
 *   <li>protected paths without a valid {@code BG_AUTH} cookie answer 401 {@code UNAUTHENTICATED} as an
 *       {@code ApiError};</li>
 *   <li>a mutating request without a matching {@code X-XSRF-TOKEN} is rejected and nothing is processed. The approved
 *       contract states that 403 is not used and declares no 403 response, so the rejection is the same 401
 *       {@code UNAUTHENTICATED} {@code ApiError} (this deviates from the "403" wording of task T044; recorded there);</li>
 *   <li>public operations need neither cookie nor token; a stale, expired or tampered token is not accepted;</li>
 *   <li>the {@code XSRF-TOKEN} cookie is readable, scoped to {@code /} and SameSite=Strict.</li>
 * </ul>
 * No controller exists yet in this phase, so an accepted request is recognised by <em>not</em> being a 401: it reaches
 * the dispatcher and ends in the generic 404 {@code NOT_FOUND}.
 */
class SecurityBaselineIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.now();
    private static final String CSRF = "csrf-token-for-tests";

    @MockitoBean
    TokenVersionLookup tokenVersionLookup;

    @BeforeEach
    void customerSevenHasVersionTwo() {
        when(tokenVersionLookup.currentTokenVersion(anyLong())).thenReturn(OptionalInt.empty());
        when(tokenVersionLookup.currentTokenVersion(7L)).thenReturn(OptionalInt.of(2));
    }

    private static JwtService jwtAt(Instant now) {
        SecurityProperties props = new SecurityProperties(new SecurityProperties.Jwt(JWT_SECRET, 60),
                new SecurityProperties.Cookie(false), new SecurityProperties.Cors(List.of()));
        return new JwtService(props, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Cookie auth(String token) {
        return new Cookie("BG_AUTH", token);
    }

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    // ---- 401 UNAUTHENTICATED for protected paths ------------------------------------------------------------------

    @Test
    void protectedReadWithoutCookieIsUnauthenticatedApiError() throws Exception {
        for (String path : List.of("/api/v1/auth/me", "/api/v1/addresses", "/api/v1/basket", "/api/v1/orders",
                "/api/v1/orders/1", "/api/v1/rewards/me", "/api/v1/recommendations")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.message").value("Please log in to continue."))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist());
        }
    }

    @Test
    void unknownPathsAreAlsoProtectedByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/admin")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedExpiredStaleAndUnknownCustomerTokensAreUnauthenticated() throws Exception {
        String valid = jwtAt(NOW).issue(7L, 2);
        String tampered = valid.substring(0, valid.length() - 3) + (valid.endsWith("AAA") ? "BBB" : "AAA");
        String expired = jwtAt(NOW.minus(Duration.ofHours(3))).issue(7L, 2);
        String staleVersion = jwtAt(NOW).issue(7L, 1);          // logout bumped the version to 2
        String unknownCustomer = jwtAt(NOW).issue(99L, 0);

        for (String token : List.of(tampered, expired, staleVersion, unknownCustomer, "garbage")) {
            mockMvc.perform(get("/api/v1/auth/me").cookie(auth(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void validCookiePassesAuthentication() throws Exception {
        String token = jwtAt(NOW).issue(7L, 2);
        // Customer 7 only exists in the mocked lookup, so a profile read would fail; any other protected path shows
        // that authentication itself succeeded (404 until its controller exists, then a normal response).
        MvcResult result = mockMvc.perform(get("/api/v1/addresses").cookie(auth(token))).andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    // ---- CSRF ----------------------------------------------------------------------------------------------------

    @Test
    void mutatingRequestsWithoutCsrfTokenAreRejectedAndNeverReachAHandler() throws Exception {
        Cookie auth = auth(jwtAt(NOW).issue(7L, 2));
        for (MockHttpServletRequestBuilder request : List.of(
                post("/api/v1/addresses").contentType(MediaType.APPLICATION_JSON).content("{}"),
                put("/api/v1/addresses/1").contentType(MediaType.APPLICATION_JSON).content("{}"),
                delete("/api/v1/addresses/1"),
                post("/api/v1/auth/logout"),
                post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            mockMvc.perform(request.cookie(auth))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void mismatchingCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(auth(jwtAt(NOW).issue(7L, 2)))
                        .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", "a-different-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void mutatingRequestWithCookieAndMatchingCsrfTokenPassesSecurity() throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/v1/addresses")
                        .cookie(auth(jwtAt(NOW).issue(7L, 2)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))).andReturn();
        assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
    }

    @Test
    void validCsrfWithoutAuthenticationCookieIsStillUnauthenticated() throws Exception {
        mockMvc.perform(withCsrf(post("/api/v1/addresses").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---- public operations ----------------------------------------------------------------------------------------

    @Test
    void publicOperationsNeedNeitherCookieNorCsrfToken() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/v1/categories"), get("/api/v1/brands"), get("/api/v1/books"),
                get("/api/v1/books/filter-options"), get("/api/v1/books/5"), get("/api/v1/books/5/related"),
                get("/api/v1/reference/states"),
                post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"),
                post("/api/v1/basket/preview").contentType(MediaType.APPLICATION_JSON).content("{}"))) {
            MvcResult result = mockMvc.perform(request).andReturn();
            assertThat(result.getResponse().getStatus()).as(result.getRequest().getRequestURI()).isNotEqualTo(401);
        }
    }

    @Test
    void unmatchedRouteIsAGenericNotFoundApiErrorWithoutDetails() throws Exception {
        mockMvc.perform(get("/api/v1/books/no-such-route/details"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested item was not found."))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---- cookies and headers --------------------------------------------------------------------------------------

    @Test
    void csrfCookieIsReadableScopedToRootAndSameSiteStrict() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/categories")).andReturn();
        // Servlet 6 keeps SameSite as a cookie attribute; the container renders it into the Set-Cookie header.
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("XSRF-TOKEN cookie is set on a response").isNotNull();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.isHttpOnly()).as("readable by the SPA").isFalse();
        assertThat(cookie.getSecure()).as("test profile uses plain HTTP").isFalse();
    }

    @Test
    void securityHeadersAreSetAndNoSessionIsCreated() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/categories")).andReturn();
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(result.getResponse().getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
    }

    @Test
    void httpBasicAndFormLoginAreNotAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Basic dGVzdDp0ZXN0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        mockMvc.perform(get("/login")).andExpect(status().isUnauthorized());
    }
}
