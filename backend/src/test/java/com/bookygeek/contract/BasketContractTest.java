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
 * Validates the real responses of {@code previewBasket}, {@code getBasket}, {@code addBasketItem},
 * {@code updateBasketItem}, {@code removeBasketItem} and {@code mergeGuestBasket} against {@code api/openapi.yaml}
 * (T086): declared status codes, body schemas (closed: no undeclared property) and the {@code ApiError} shape.
 */
class BasketContractTest extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";

    private Cookie login() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"Asha Verma\",\"email\":\"asha.verma@example.com\","
                        + "\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}")).andReturn();
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha.verma@example.com\",\"password\":\"" + PASSWORD + "\"}")).andReturn()
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

    // ---- previewBasket --------------------------------------------------------------------------------------------

    @Test
    void previewBasketMatchesTheContract() throws Exception {
        check("previewBasket", 200, json(post("/api/v1/basket/preview"),
                "{\"items\":[{\"bookId\":1,\"quantity\":2},{\"bookId\":13,\"quantity\":1},{\"bookId\":5,\"quantity\":1}]}"));
        check("previewBasket", 400, json(post("/api/v1/basket/preview"), "{\"items\":[]}"));
        check("previewBasket", 400, json(post("/api/v1/basket/preview"),
                "{\"items\":[{\"bookId\":1,\"quantity\":11}]}"));
    }

    // ---- getBasket ------------------------------------------------------------------------------------------------

    @Test
    void getBasketMatchesTheContractEmptyFilledAndUnauthenticated() throws Exception {
        Cookie auth = login();
        check("getBasket", 200, mockMvc.perform(get("/api/v1/basket").cookie(auth)).andReturn());
        json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":1,\"quantity\":2}");
        MvcResult filled = mockMvc.perform(get("/api/v1/basket").cookie(auth)).andReturn();
        check("getBasket", 200, filled);
        assertThat(filled.getResponse().getContentAsString()).contains("\"stockQuantity\":25");
        check("getBasket", 401, mockMvc.perform(get("/api/v1/basket")).andReturn());
    }

    // ---- addBasketItem --------------------------------------------------------------------------------------------

    @Test
    void addBasketItemMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        check("addBasketItem", 200, json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":1}"));
        check("addBasketItem", 200, json(authed(post("/api/v1/basket/items"), auth),
                "{\"bookId\":13,\"quantity\":10}"));
        check("addBasketItem", 400, json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":1,\"quantity\":0}"));
        check("addBasketItem", 401, json(post("/api/v1/basket/items"), "{\"bookId\":1}"));
        check("addBasketItem", 404, json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":9999}"));
        check("addBasketItem", 409, json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":13}"));
        check("addBasketItem", 409, json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":5}"));
    }

    // ---- updateBasketItem -----------------------------------------------------------------------------------------

    @Test
    void updateBasketItemMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":1}");
        jdbc.update("UPDATE book SET stock_quantity = 4 WHERE id = 1");

        check("updateBasketItem", 200, json(authed(put("/api/v1/basket/items/1"), auth), "{\"quantity\":3}"));
        check("updateBasketItem", 400, json(authed(put("/api/v1/basket/items/1"), auth), "{\"quantity\":0}"));
        check("updateBasketItem", 401, json(put("/api/v1/basket/items/1"), "{\"quantity\":3}"));
        check("updateBasketItem", 404, json(authed(put("/api/v1/basket/items/2"), auth), "{\"quantity\":3}"));
        check("updateBasketItem", 409, json(authed(put("/api/v1/basket/items/1"), auth), "{\"quantity\":5}"));
    }

    // ---- removeBasketItem -----------------------------------------------------------------------------------------

    @Test
    void removeBasketItemMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":1}");

        check("removeBasketItem", 401, mockMvc.perform(delete("/api/v1/basket/items/1")).andReturn());
        check("removeBasketItem", 200, mockMvc.perform(authed(delete("/api/v1/basket/items/1"), auth)).andReturn());
        check("removeBasketItem", 404, mockMvc.perform(authed(delete("/api/v1/basket/items/1"), auth)).andReturn());
    }

    // ---- mergeGuestBasket -----------------------------------------------------------------------------------------

    @Test
    void mergeGuestBasketMatchesTheContractWithAndWithoutAdjustments() throws Exception {
        Cookie auth = login();
        check("mergeGuestBasket", 200, json(authed(post("/api/v1/basket/merge"), auth),
                "{\"items\":[{\"bookId\":1,\"quantity\":2}]}"));
        MvcResult adjusted = json(authed(post("/api/v1/basket/merge"), auth),
                "{\"items\":[{\"bookId\":5,\"quantity\":1},{\"bookId\":1,\"quantity\":10},{\"bookId\":1,\"quantity\":10}]}");
        check("mergeGuestBasket", 200, adjusted);
        assertThat(adjusted.getResponse().getContentAsString()).contains("UNAVAILABLE").contains("QUANTITY_REDUCED");
        check("mergeGuestBasket", 400, json(authed(post("/api/v1/basket/merge"), auth), "{\"items\":[]}"));
        check("mergeGuestBasket", 401, json(post("/api/v1/basket/merge"),
                "{\"items\":[{\"bookId\":1,\"quantity\":1}]}"));
    }
}
