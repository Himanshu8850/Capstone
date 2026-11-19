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
 * Validates the real responses of {@code listOrders}, {@code cancelOrder}, {@code buyAgain} and
 * {@code getBasketRecommendations} against {@code api/openapi.yaml} (T118): every declared status code, the closed
 * {@code OrderSummary}, {@code Order}, {@code BasketWithReport} and {@code BookSummary} schemas, and the
 * {@code ApiError} shape of both 409 codes of {@code cancelOrder}. {@code getOrder} is covered by
 * {@link CheckoutContractTest}.
 */
class OrderContractTest extends IntegrationTestBase {

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
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        ContractSupport.assertResponse(operationId, result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    private void add(Cookie auth, long bookId, int quantity) throws Exception {
        mockMvc.perform(authed(post("/api/v1/basket/items"), auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + ",\"quantity\":" + quantity + "}")).andReturn();
    }

    /** Places an order and returns its id; {@code points} are redeemed against the stated payable total. */
    private long place(Cookie auth, String method, String payment, int points, String expected) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/v1/orders"), auth)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":" + ADDRESS + ",\"paymentMethod\":\"" + method + "\","
                        + (payment == null ? "" : "\"payment\":" + payment + ",")
                        + "\"pointsToRedeem\":" + points + ",\"expectedPayableTotal\":\"" + expected + "\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return jdbc.queryForObject("SELECT max(id) FROM orders", Long.class);
    }

    private MvcResult cancel(Cookie auth, long orderId) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth)).andReturn();
    }

    // ---- listOrders -------------------------------------------------------------------------------------------------

    @Test
    void listOrdersMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        check("listOrders", 200, mockMvc.perform(get("/api/v1/orders").cookie(auth)).andReturn()); // empty array
        add(auth, 1, 2);
        add(auth, 13, 1);
        place(auth, "CREDIT_CARD", CARD, 100, "1091.85");
        add(auth, 1, 1);
        long cancelled = place(auth, "COD", null, 0, "563.95");
        cancel(auth, cancelled);

        MvcResult list = mockMvc.perform(get("/api/v1/orders").cookie(auth)).andReturn();
        check("listOrders", 200, list);
        assertThat(list.getResponse().getContentAsString()).contains("\"status\":\"CANCELLED\"")
                .contains("\"status\":\"PLACED\"");
        check("listOrders", 401, mockMvc.perform(get("/api/v1/orders")).andReturn());
    }

    // ---- cancelOrder ------------------------------------------------------------------------------------------------

    @Test
    void cancelOrderMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        add(auth, 1, 2);
        // 998.00 + 49.90 + 40.00 = 1087.90, 100 points => 987.90
        long orderId = place(auth, "CREDIT_CARD", CARD, 100, "987.90");

        check("cancelOrder", 401, mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancellation")
                .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF)).andReturn());
        check("cancelOrder", 404, cancel(auth, 999999));
        MvcResult cancelled = cancel(auth, orderId);
        check("cancelOrder", 200, cancelled);
        assertThat(cancelled.getResponse().getContentAsString()).contains("\"status\":\"CANCELLED\"")
                .contains("\"paymentStatus\":\"REFUNDED\"");

        MvcResult again = cancel(auth, orderId);
        check("cancelOrder", 409, again);
        assertThat(again.getResponse().getContentAsString()).contains("ALREADY_CANCELLED");
    }

    @Test
    void cancelOrderMatchesTheContractForAnExpiredWindowAndACodOrder() throws Exception {
        Cookie auth = login();
        add(auth, 1, 1);
        long expired = place(auth, "COD", null, 0, "563.95");
        jdbc.update("UPDATE orders SET placed_at = now() - interval '49 hours' WHERE id = ?", expired);
        add(auth, 1, 1);
        long open = place(auth, "COD", null, 0, "563.95");

        MvcResult tooLate = cancel(auth, expired);
        check("cancelOrder", 409, tooLate);
        assertThat(tooLate.getResponse().getContentAsString()).contains("CANCELLATION_WINDOW_EXPIRED");
        MvcResult cod = cancel(auth, open);
        check("cancelOrder", 200, cod);
        assertThat(cod.getResponse().getContentAsString()).contains("\"paymentStatus\":\"CANCELLED\"");
    }

    // ---- buyAgain ---------------------------------------------------------------------------------------------------

    @Test
    void buyAgainMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        add(auth, 1, 2);
        long orderId = place(auth, "COD", null, 0, "1087.90");

        check("buyAgain", 200, mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/buy-again"), auth))
                .andReturn());
        check("buyAgain", 401, mockMvc.perform(post("/api/v1/orders/" + orderId + "/buy-again")
                .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF)).andReturn());
        check("buyAgain", 404, mockMvc.perform(authed(post("/api/v1/orders/999999/buy-again"), auth)).andReturn());
    }

    @Test
    void buyAgainMatchesTheContractWhenTheReportListsAdjustments() throws Exception {
        Cookie auth = login();
        add(auth, 6, 1);
        add(auth, 1, 1);
        // 899.00 + 499.00 = 1398.00, tax 69.90, delivery 40.00 => 1507.90
        long orderId = place(auth, "COD", null, 0, "1507.90");

        MvcResult result = mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/buy-again"), auth)).andReturn();

        check("buyAgain", 200, result);
        assertThat(result.getResponse().getContentAsString()).contains("\"reason\":\"UNAVAILABLE\"");
    }

    // ---- getBasketRecommendations -----------------------------------------------------------------------------------

    @Test
    void getBasketRecommendationsMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();
        check("getBasketRecommendations", 200, mockMvc.perform(get("/api/v1/recommendations").cookie(auth)).andReturn());
        check("getBasketRecommendations", 401, mockMvc.perform(get("/api/v1/recommendations")).andReturn());

        add(auth, 1, 1);
        place(auth, "COD", null, 0, "563.95");
        MvcResult recommended = mockMvc.perform(get("/api/v1/recommendations").cookie(auth)).andReturn();
        check("getBasketRecommendations", 200, recommended);
        assertThat(recommended.getResponse().getContentAsString()).startsWith("[{");
    }
}
