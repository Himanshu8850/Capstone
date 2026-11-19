package com.bookygeek.contract;

import com.bookygeek.integration.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Validates the real responses of {@code quoteCheckout}, {@code placeOrder} and {@code getOrder} against
 * {@code api/openapi.yaml} (T102): every declared status code (including all five 409 codes of {@code placeOrder}),
 * closed body schemas, the {@code Location} header on 201 and the {@code ApiError} shape.
 */
class CheckoutContractTest extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";
    private static final String ADDRESS = "{\"fullName\":\"Asha Verma\",\"phone\":\"9876543210\","
            + "\"addressLine1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pinCode\":\"560001\"}";
    private static final String CARD = "{\"card\":{\"number\":\"4111-1111-1111-1111\",\"nameOnCard\":\"Asha Verma\","
            + "\"cvv\":\"123\",\"expiry\":\"12/2099\"}}";

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

    private void add(Cookie auth, long bookId, int quantity) throws Exception {
        json(authed(post("/api/v1/basket/items"), auth), "{\"bookId\":" + bookId + ",\"quantity\":" + quantity + "}");
    }

    private MvcResult place(Cookie auth, UUID key, String method, String payment, int points, String expected)
            throws Exception {
        String body = "{\"address\":" + ADDRESS + ",\"paymentMethod\":\"" + method + "\","
                + (payment == null ? "" : "\"payment\":" + payment + ",") + "\"pointsToRedeem\":" + points
                + ",\"expectedPayableTotal\":\"" + expected + "\"}";
        return json(authed(post("/api/v1/orders"), auth).header("Idempotency-Key", key.toString()), body);
    }

    // ---- quoteCheckout --------------------------------------------------------------------------------------------

    @Test
    void quoteCheckoutMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        check("quoteCheckout", 409, json(authed(post("/api/v1/checkout/quote"), auth), "{\"pointsToRedeem\":0}"));

        add(auth, 1, 1);
        check("quoteCheckout", 200, json(authed(post("/api/v1/checkout/quote"), auth), "{\"pointsToRedeem\":100}"));
        check("quoteCheckout", 400, json(authed(post("/api/v1/checkout/quote"), auth), "{\"pointsToRedeem\":-1}"));
        check("quoteCheckout", 400, json(authed(post("/api/v1/checkout/quote"), auth), "{}"));
        check("quoteCheckout", 401, json(post("/api/v1/checkout/quote"), "{\"pointsToRedeem\":0}"));
        check("quoteCheckout", 409, json(authed(post("/api/v1/checkout/quote"), auth), "{\"pointsToRedeem\":999}"));
    }

    @Test
    void quoteCheckoutWithABasketIssueMatchesTheContract() throws Exception {
        Cookie auth = login();
        add(auth, 1, 3);
        jdbc.update("UPDATE book SET stock_quantity = 0 WHERE id = 1");

        MvcResult quote = json(authed(post("/api/v1/checkout/quote"), auth), "{\"pointsToRedeem\":0}");

        check("quoteCheckout", 200, quote);
        assertThat(quote.getResponse().getContentAsString()).contains("OUT_OF_STOCK");
    }

    // ---- placeOrder -----------------------------------------------------------------------------------------------

    @Test
    void placeOrderMatchesTheContractFor201And200Replay() throws Exception {
        Cookie auth = login();
        add(auth, 1, 1);
        UUID key = UUID.randomUUID();

        MvcResult created = place(auth, key, "CREDIT_CARD", CARD, 100, "463.95");
        check("placeOrder", 201, created);
        assertThat(created.getResponse().getHeader("Location")).startsWith("/api/v1/orders/");
        check("placeOrder", 200, place(auth, key, "CREDIT_CARD", CARD, 100, "463.95"));
    }

    @Test
    void placeOrderMatchesTheContractForCodAndEBookOnlyOrders() throws Exception {
        Cookie auth = login();
        add(auth, 13, 1);
        check("placeOrder", 201, place(auth, UUID.randomUUID(), "UPI", "{\"upi\":{\"upiId\":\"asha@okbank\"}}", 0,
                "103.95"));
        add(auth, 1, 1);
        check("placeOrder", 201, place(auth, UUID.randomUUID(), "COD", null, 0, "563.95"));
    }

    @Test
    void placeOrderMatchesTheContractForEveryErrorStatus() throws Exception {
        Cookie auth = login();

        check("placeOrder", 409, place(auth, UUID.randomUUID(), "COD", null, 0, "563.95")); // EMPTY_BASKET
        add(auth, 1, 1);
        check("placeOrder", 400, place(auth, UUID.randomUUID(), "CREDIT_CARD", null, 0, "563.95"));
        check("placeOrder", 400, json(authed(post("/api/v1/orders"), auth), "{}"));
        check("placeOrder", 400, json(authed(post("/api/v1/orders"), auth),
                "{\"address\":" + ADDRESS + ",\"paymentMethod\":\"COD\",\"pointsToRedeem\":0,"
                        + "\"expectedPayableTotal\":\"563.95\"}")); // no Idempotency-Key
        check("placeOrder", 401, json(post("/api/v1/orders").header("Idempotency-Key", UUID.randomUUID().toString()),
                "{}"));
        check("placeOrder", 409, place(auth, UUID.randomUUID(), "COD", null, 0, "1.00")); // BASKET_CHANGED
        check("placeOrder", 409, place(auth, UUID.randomUUID(), "COD", null, 999, "0.01")); // POINTS_LIMIT_EXCEEDED
        MvcResult declined = place(auth, UUID.randomUUID(), "UPI", "{\"upi\":{\"upiId\":\"failure@upi\"}}", 0,
                "563.95");
        check("placeOrder", 409, declined);
        assertThat(declined.getResponse().getContentAsString()).contains("PAYMENT_DECLINED");
        jdbc.update("UPDATE book SET stock_quantity = 0 WHERE id = 1");
        MvcResult noStock = place(auth, UUID.randomUUID(), "COD", null, 0, "563.95");
        check("placeOrder", 409, noStock);
        assertThat(noStock.getResponse().getContentAsString()).contains("OUT_OF_STOCK");
    }

    // ---- getOrder -------------------------------------------------------------------------------------------------

    @Test
    void getOrderMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        add(auth, 1, 2);
        add(auth, 13, 1);
        MvcResult placed = place(auth, UUID.randomUUID(), "COD", null, 0, "1191.85");
        assertThat(placed.getResponse().getStatus()).isEqualTo(201);
        String location = placed.getResponse().getHeader("Location");

        check("getOrder", 200, mockMvc.perform(get(location).cookie(auth)).andReturn());
        check("getOrder", 401, mockMvc.perform(get(location)).andReturn());
        check("getOrder", 404, mockMvc.perform(get("/api/v1/orders/999999").cookie(auth)).andReturn());
    }
}
