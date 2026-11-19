package com.bookygeek.contract;

import com.bookygeek.integration.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Validates the real responses of {@code register}, {@code login}, {@code logout} and {@code getCurrentCustomer}
 * against {@code api/openapi.yaml} (T050): declared status codes, body schemas, the {@code ApiError} shape for every
 * error response, and the headers the contract promises.
 */
class AuthContractTest extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";
    private static final String REGISTER = "{\"fullName\":\"Asha Verma\",\"email\":\"asha.verma@example.com\","
            + "\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}\n";

    private MvcResult postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private static void check(String operationId, MvcResult result) throws Exception {
        ContractSupport.assertResponse(operationId, result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private Cookie loginCookie() throws Exception {
        postJson("/api/v1/auth/register", REGISTER);
        MvcResult login = postJson("/api/v1/auth/login",
                "{\"email\":\"asha.verma@example.com\",\"password\":\"" + PASSWORD + "\"}");
        return login.getResponse().getCookie("BG_AUTH");
    }

    // ---- register -------------------------------------------------------------------------------------------------

    @Test
    void registerCreatedMatchesTheContract() throws Exception {
        MvcResult result = postJson("/api/v1/auth/register", REGISTER);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).isNotBlank();
        check("register", result);
    }

    @Test
    void registerValidationFailedMatchesTheApiErrorSchema() throws Exception {
        MvcResult result = postJson("/api/v1/auth/register",
                "{\"fullName\":\"\",\"email\":\"x\",\"password\":\"1\",\"confirmPassword\":\"\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        check("register", result);
        assertThat(result.getResponse().getContentAsString()).contains("\"fieldErrors\"");
    }

    @Test
    void registerEmailTakenMatchesTheApiErrorSchema() throws Exception {
        postJson("/api/v1/auth/register", REGISTER);
        MvcResult result = postJson("/api/v1/auth/register", REGISTER);
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        check("register", result);
    }

    // ---- login ----------------------------------------------------------------------------------------------------

    @Test
    void loginOkMatchesTheContractAndSetsTheDeclaredCookies() throws Exception {
        postJson("/api/v1/auth/register", REGISTER);
        MvcResult result = postJson("/api/v1/auth/login",
                "{\"email\":\"asha.verma@example.com\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        check("login", result);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("BG_AUTH="));
        // MockMvc exposes the CSRF cookie (written through addCookie) via getCookie, not as a Set-Cookie header.
        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
    }

    @Test
    void loginValidationFailedAndInvalidCredentialsMatchTheApiErrorSchema() throws Exception {
        MvcResult invalid = postJson("/api/v1/auth/login", "{\"email\":\"\",\"password\":\"\"}");
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
        check("login", invalid);

        MvcResult wrong = postJson("/api/v1/auth/login", "{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        check("login", wrong);
    }

    // ---- logout ---------------------------------------------------------------------------------------------------

    @Test
    void logoutNoContentHasNoBodyAndExpiresTheCookies() throws Exception {
        Cookie auth = loginCookie();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout").cookie(auth)
                .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        check("logout", result);
        assertThat(result.getResponse().getHeaders("Set-Cookie")).anyMatch(c -> c.startsWith("BG_AUTH=") && c.contains("Max-Age=0"));
    }

    @Test
    void logoutUnauthenticatedMatchesTheApiErrorSchema() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        check("logout", result);
    }

    // ---- getCurrentCustomer ---------------------------------------------------------------------------------------

    @Test
    void getCurrentCustomerOkAndUnauthenticatedMatchTheContract() throws Exception {
        Cookie auth = loginCookie();
        MvcResult ok = mockMvc.perform(get("/api/v1/auth/me").cookie(auth)).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        check("getCurrentCustomer", ok);

        MvcResult unauthenticated = mockMvc.perform(get("/api/v1/auth/me")).andReturn();
        assertThat(unauthenticated.getResponse().getStatus()).isEqualTo(401);
        check("getCurrentCustomer", unauthenticated);
    }

    // ---- the checker itself must be able to fail ------------------------------------------------------------------

    @Test
    void theContractCheckRejectsUndeclaredStatusesMissingFieldsAndLeakedProperties() {
        assertThatThrownBy(() -> ContractSupport.assertResponse("register", 403, "{}"))
                .hasMessageContaining("does not declare status 403");
        assertThatThrownBy(() -> ContractSupport.assertResponse("getCurrentCustomer", 200, "{\"id\":1}"))
                .hasMessageContaining("missing required property 'fullName'");
        assertThatThrownBy(() -> ContractSupport.assertResponse("getCurrentCustomer", 200,
                "{\"id\":1,\"fullName\":\"A\",\"email\":\"a@b.co\",\"passwordHash\":\"x\"}"))
                .hasMessageContaining("passwordHash: property is not declared");
        assertThatThrownBy(() -> ContractSupport.assertResponse("login", 401,
                "{\"status\":401,\"code\":\"SOMETHING_ELSE\",\"message\":\"m\",\"timestamp\":\"2026-11-12T10:15:30Z\"}"))
                .hasMessageContaining("is not one of");
        assertThatThrownBy(() -> ContractSupport.assertResponse("logout", 204, "unexpected"))
                .hasMessageContaining("declares no body");
    }
}
