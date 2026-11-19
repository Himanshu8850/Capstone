package com.bookygeek.integration;

import com.bookygeek.common.security.AuthCookieFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.List;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static com.bookygeek.testsupport.TestCredentials.WRONG_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Register, login, current customer and logout end to end against PostgreSQL (T049): one-transaction creation of the
 * customer and the 1,000-point reward account, the error codes, the cookie flags, and token invalidation by logout.
 */
class AuthFlowIT extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";
    private static final String REGISTER_BODY = "{\"fullName\":\"Asha Verma\",\"email\":\"asha.verma@example.com\","
            + "\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}\n";

    private static String registerBody(String email) {
        return REGISTER_BODY.replace("asha.verma@example.com", email);
    }

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    private void register(String body) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private Cookie authCookieOf(MvcResult login) {
        Cookie cookie = login.getResponse().getCookie(AuthCookieFactory.COOKIE_NAME);
        assertThat(cookie).as("BG_AUTH cookie").isNotNull();
        return cookie;
    }

    // ---- register -------------------------------------------------------------------------------------------------

    @Test
    void registerCreatesTheCustomerAndTheRewardAccountWithOneThousandPoints() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fullName").value("Asha Verma"))
                .andExpect(jsonPath("$.email").value("asha.verma@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM reward_account", Integer.class)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reward_transaction", Integer.class)).isZero();
        String hash = jdbc.queryForObject("SELECT password_hash FROM customer", String.class);
        assertThat(hash).startsWith("{pbkdf2}").doesNotContain(PASSWORD);
    }

    @Test
    void registerDoesNotSignTheCustomerIn() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY)).andReturn();
        assertThat(result.getResponse().getCookie(AuthCookieFactory.COOKIE_NAME)).isNull();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/v1/auth/me");
    }

    @Test
    void emailIsStoredLowerCasedAndAnyLetterCaseOfItIsADuplicate() throws Exception {
        register(registerBody("Asha.Verma@Example.COM"));
        assertThat(jdbc.queryForObject("SELECT email FROM customer", String.class)).isEqualTo("asha.verma@example.com");

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("ASHA.VERMA@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("An account with this e-mail address already exists."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reward_account", Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidRegistrationListsEveryInvalidFieldAndCreatesNothing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" \",\"email\":\"nope\",\"password\":\"short\",\"confirmPassword\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='fullName')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='password')]").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class)).isZero();
    }

    @Test
    void mismatchingConfirmationIsReportedOnConfirmPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY.replace("\"confirmPassword\":\"" + PASSWORD + "\"", "\"confirmPassword\":\"" + WRONG_PASSWORD + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("confirmPassword"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Integer.class)).isZero();
    }

    @Test
    void malformedJsonIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- login ----------------------------------------------------------------------------------------------------

    @Test
    void loginSetsAHttpOnlySameSiteStrictCookieScopedToApi() throws Exception {
        register(REGISTER_BODY);

        MvcResult result = login("ASHA.verma@example.com", PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Cookie cookie = authCookieOf(result);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofMinutes(60).toSeconds());
        // The test profile runs over plain HTTP; Secure is true in every other profile.
        assertThat(cookie.getSecure()).isFalse();
        assertThat(result.getResponse().getContentAsString())
                .contains("\"fullName\":\"Asha Verma\"").doesNotContain(PASSWORD).doesNotContain("pbkdf2");
        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).as("CSRF cookie is issued").isNotNull();
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGenericError() throws Exception {
        register(REGISTER_BODY);

        for (MvcResult result : List.of(login("asha.verma@example.com", WRONG_PASSWORD),
                login("nobody@example.com", PASSWORD))) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getContentAsString())
                    .contains("\"code\":\"INVALID_CREDENTIALS\"")
                    .contains("The e-mail or password is incorrect.");
            assertThat(result.getResponse().getCookie(AuthCookieFactory.COOKIE_NAME)).isNull();
        }
    }

    @Test
    void loginWithBlankFieldsIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- current customer and logout ------------------------------------------------------------------------------

    @Test
    void getCurrentCustomerReturnsTheSignedInCustomer() throws Exception {
        register(REGISTER_BODY);
        Cookie auth = authCookieOf(login("asha.verma@example.com", PASSWORD));

        mockMvc.perform(get("/api/v1/auth/me").cookie(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha Verma"))
                .andExpect(jsonPath("$.email").value("asha.verma@example.com"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void anAuthenticatedRequestDoesNotExpireTheCsrfCookieSoTheNextStateChangingCallStillWorks() throws Exception {
        register(REGISTER_BODY);
        Cookie auth = authCookieOf(login("asha.verma@example.com", PASSWORD));

        MvcResult me = mockMvc.perform(get("/api/v1/auth/me").cookie(auth).cookie(new Cookie("XSRF-TOKEN", CSRF)))
                .andExpect(status().isOk()).andReturn();

        Cookie csrf = me.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrf == null || csrf.getMaxAge() != 0).as("the CSRF cookie must not be expired").isTrue();
    }

    @Test
    void getCurrentCustomerWithoutCookieIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutExpiresBothCookiesBumpsTheTokenVersionAndRejectsAReplayedCookie() throws Exception {
        register(REGISTER_BODY);
        Cookie auth = authCookieOf(login("asha.verma@example.com", PASSWORD));
        assertThat(jdbc.queryForObject("SELECT token_version FROM customer", Integer.class)).isZero();

        MvcResult logout = mockMvc.perform(withCsrf(post("/api/v1/auth/logout").cookie(auth))).andReturn();

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        Cookie expired = logout.getResponse().getCookie(AuthCookieFactory.COOKIE_NAME);
        assertThat(expired).isNotNull();
        assertThat(expired.getMaxAge()).isZero();
        assertThat(expired.getPath()).isEqualTo("/api");
        Cookie expiredCsrf = logout.getResponse().getCookie("XSRF-TOKEN");
        assertThat(expiredCsrf).isNotNull();
        assertThat(expiredCsrf.getMaxAge()).isZero();
        assertThat(jdbc.queryForObject("SELECT token_version FROM customer", Integer.class)).isEqualTo(1);

        // The old token still has a valid signature and expiry, but its version is stale.
        mockMvc.perform(get("/api/v1/auth/me").cookie(auth))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aNewLoginAfterLogoutWorksAgainWithTheNewVersion() throws Exception {
        register(REGISTER_BODY);
        Cookie first = authCookieOf(login("asha.verma@example.com", PASSWORD));
        mockMvc.perform(withCsrf(post("/api/v1/auth/logout").cookie(first))).andExpect(status().isNoContent());

        Cookie second = authCookieOf(login("asha.verma@example.com", PASSWORD));

        mockMvc.perform(get("/api/v1/auth/me").cookie(second)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").cookie(first)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithoutAuthenticationOrWithoutCsrfTokenIsRejectedAndChangesNothing() throws Exception {
        register(REGISTER_BODY);
        Cookie auth = authCookieOf(login("asha.verma@example.com", PASSWORD));

        mockMvc.perform(post("/api/v1/auth/logout").cookie(auth))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(withCsrf(post("/api/v1/auth/logout")))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT token_version FROM customer", Integer.class)).isZero();
        mockMvc.perform(get("/api/v1/auth/me").cookie(auth)).andExpect(status().isOk());
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        register(REGISTER_BODY);
        long customerId = jdbc.queryForObject("SELECT id FROM customer", Long.class);
        // Signed with the real secret but issued two hours ago, so its 60-minute lifetime has passed.
        var past = java.time.Clock.fixed(java.time.Instant.now().minus(Duration.ofHours(2)), java.time.ZoneOffset.UTC);
        var props = new com.bookygeek.common.security.SecurityProperties(
                new com.bookygeek.common.security.SecurityProperties.Jwt(JWT_SECRET, 60),
                new com.bookygeek.common.security.SecurityProperties.Cookie(false),
                new com.bookygeek.common.security.SecurityProperties.Cors(List.of()));
        String expired = new com.bookygeek.common.security.JwtService(props, past).issue(customerId, 0);

        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(AuthCookieFactory.COOKIE_NAME, expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aTokenForADeletedCustomerIsRejected() throws Exception {
        register(REGISTER_BODY);
        Cookie auth = authCookieOf(login("asha.verma@example.com", PASSWORD));
        jdbc.execute("TRUNCATE TABLE customer RESTART IDENTITY CASCADE");

        mockMvc.perform(get("/api/v1/auth/me").cookie(auth)).andExpect(status().isUnauthorized());
    }

    @Test
    void errorBodiesNeverRevealInternals() throws Exception {
        register(REGISTER_BODY);
        MvcResult duplicate = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(REGISTER_BODY)).andReturn();
        String body = duplicate.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("SQL").doesNotContain("uq_customer_email")
                .doesNotContain("at com.");
    }
}
