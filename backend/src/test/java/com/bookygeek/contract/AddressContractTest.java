package com.bookygeek.contract;

import com.bookygeek.integration.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Validates the real responses of {@code listAddresses}, {@code createAddress}, {@code updateAddress},
 * {@code deleteAddress} and {@code listStates} against {@code api/openapi.yaml} (T102): declared status codes, closed
 * body schemas and the {@code ApiError} shape.
 */
class AddressContractTest extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";
    private static final String ADDRESS = "{\"fullName\":\"Asha Verma\",\"phone\":\"9876543210\","
            + "\"addressLine1\":\"12 MG Road\",\"addressLine2\":\"Near the park\",\"city\":\"Bengaluru\","
            + "\"state\":\"Karnataka\",\"pinCode\":\"560001\"}";

    private Cookie login(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"Asha Verma\",\"email\":\"" + email
                        + "\",\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}")).andReturn();
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn()
                .getResponse().getCookie("BG_AUTH");
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, Cookie auth) {
        return request.cookie(auth).cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    private static void check(String operationId, int expectedStatus, MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        ContractSupport.assertResponse(operationId, result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private MvcResult json(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    @Test
    void listStatesMatchesTheContractAndIsPublic() throws Exception {
        check("listStates", 200, mockMvc.perform(get("/api/v1/reference/states")).andReturn());
    }

    @Test
    void listAddressesMatchesTheContractEmptyFilledAndUnauthenticated() throws Exception {
        Cookie auth = login("asha.verma@example.com");
        check("listAddresses", 200, mockMvc.perform(get("/api/v1/addresses").cookie(auth)).andReturn());
        check("createAddress", 201, json(authed(post("/api/v1/addresses"), auth), ADDRESS));
        MvcResult filled = mockMvc.perform(get("/api/v1/addresses").cookie(auth)).andReturn();
        check("listAddresses", 200, filled);
        assertThat(filled.getResponse().getContentAsString()).contains("Near the park");
        check("listAddresses", 401, mockMvc.perform(get("/api/v1/addresses")).andReturn());
    }

    @Test
    void createAddressMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login("asha.verma@example.com");
        MvcResult created = json(authed(post("/api/v1/addresses"), auth), ADDRESS);
        check("createAddress", 201, created);
        assertThat(created.getResponse().getHeader("Location")).startsWith("/api/v1/addresses/");
        check("createAddress", 400, json(authed(post("/api/v1/addresses"), auth), "{}"));
        check("createAddress", 400, json(authed(post("/api/v1/addresses"), auth),
                ADDRESS.replace("Karnataka", "Atlantis")));
        check("createAddress", 401, json(post("/api/v1/addresses"), ADDRESS));
    }

    @Test
    void updateAddressMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login("asha.verma@example.com");
        json(authed(post("/api/v1/addresses"), auth), ADDRESS);
        Long id = jdbc.queryForObject("SELECT id FROM address", Long.class);

        check("updateAddress", 200, json(authed(put("/api/v1/addresses/" + id), auth),
                ADDRESS.replace("Bengaluru", "Mysuru")));
        check("updateAddress", 400, json(authed(put("/api/v1/addresses/" + id), auth),
                ADDRESS.replace("560001", "12")));
        check("updateAddress", 401, json(put("/api/v1/addresses/" + id), ADDRESS));
        check("updateAddress", 404, json(authed(put("/api/v1/addresses/999999"), auth), ADDRESS));
    }

    @Test
    void deleteAddressMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login("asha.verma@example.com");
        json(authed(post("/api/v1/addresses"), auth), ADDRESS);
        Long id = jdbc.queryForObject("SELECT id FROM address", Long.class);

        check("deleteAddress", 401, mockMvc.perform(delete("/api/v1/addresses/" + id)).andReturn());
        check("deleteAddress", 204, mockMvc.perform(authed(delete("/api/v1/addresses/" + id), auth)).andReturn());
        check("deleteAddress", 404, mockMvc.perform(authed(delete("/api/v1/addresses/" + id), auth)).andReturn());
    }
}
