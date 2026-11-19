package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * My Orders, order detail, Buy Again and recommendations against the real database (T118, FR-ORD-01..03, FR-BSK-07):
 * newest first and only the customer's own orders, price and item snapshots that later catalogue changes never touch,
 * Buy Again at the current price with out-of-stock, removed and over-stock books reported, and recommendations that are
 * empty without history and otherwise come from the ordered books' categories without the basket's books.
 * Book 1 is a paperback at 499 (stock 25, category 14), book 5 has no stock, book 6 is a hardcover with stock 1 and book
 * 13 is an eBook at 99; books 1 to 14 share category 14.
 */
class OrderHistoryIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CSRF = "csrf-token-for-tests";
    private static final String ADDRESS = "{\"fullName\":\"Asha Verma\",\"phone\":\"9876543210\","
            + "\"addressLine1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pinCode\":\"560001\"}";

    /** Catalogue columns this class changes that the base class does not restore. */
    private final List<Runnable> undo = new ArrayList<>();

    @AfterEach
    void restoreCatalogue() {
        undo.forEach(Runnable::run);
        undo.clear();
    }

    private Cookie loginAs(String email) throws Exception {
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

    private void add(Cookie auth, long bookId, int quantity) throws Exception {
        mockMvc.perform(authed(post("/api/v1/basket/items"), auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + ",\"quantity\":" + quantity + "}")).andReturn();
    }

    /** Pays cash on delivery for what is in the basket and returns the order id. */
    private long placeCod(Cookie auth, String expectedPayable) throws Exception {
        MvcResult result = mockMvc.perform(authed(post("/api/v1/orders"), auth)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":" + ADDRESS + ",\"paymentMethod\":\"COD\",\"pointsToRedeem\":0,"
                        + "\"expectedPayableTotal\":\"" + expectedPayable + "\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode listOrders(Cookie auth) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/orders").cookie(auth)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result);
    }

    private MvcResult buyAgain(Cookie auth, long orderId) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/buy-again"), auth)).andReturn();
    }

    private JsonNode recommendations(Cookie auth) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/recommendations").cookie(auth)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result);
    }

    private void changeCatalogue(String set, String restore, long bookId) {
        jdbc.update("UPDATE book SET " + set + " WHERE id = ?", bookId);
        undo.add(() -> jdbc.update("UPDATE book SET " + restore + " WHERE id = ?", bookId));
    }

    // ---- listOrders -------------------------------------------------------------------------------------------------

    @Test
    void aCustomerWithoutOrdersGetsAnEmptyList() throws Exception {
        assertThat(listOrders(loginAs("asha.verma@example.com")).size()).isZero();
    }

    @Test
    void ordersAreListedNewestFirstWithTheirItemsAndPayableTotal() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        long first = placeCod(auth, "563.95");
        add(auth, 13, 2);
        long second = placeCod(auth, "207.90"); // 198.00 + 9.90 tax + 0.00 delivery
        add(auth, 2, 1);
        long third = placeCod(auth, "406.45"); // 349.00 + 17.45 tax + 40.00 delivery

        JsonNode orders = listOrders(auth);

        assertThat(orders.size()).isEqualTo(3);
        assertThat(orders.get(0).get("id").asLong()).isEqualTo(third);
        assertThat(orders.get(1).get("id").asLong()).isEqualTo(second);
        assertThat(orders.get(2).get("id").asLong()).isEqualTo(first);
        assertThat(orders.get(2).get("reference").asString()).isEqualTo("BG-000001");
        assertThat(orders.get(2).get("status").asString()).isEqualTo("PLACED");
        assertThat(orders.get(2).get("payableTotal").asString()).isEqualTo("563.95");
        assertThat(orders.get(2).get("items").get(0).get("book").get("id").asLong()).isEqualTo(1);
        assertThat(orders.get(2).get("items").get(0).get("quantity").asInt()).isEqualTo(1);
        assertThat(orders.get(2).get("items").get(0).get("unitPrice").asString()).isEqualTo("499.00");
        assertThat(orders.get(1).get("items").get(0).get("lineTotal").asString()).isEqualTo("198.00");
    }

    @Test
    void aCustomerOnlySeesTheirOwnOrders() throws Exception {
        Cookie owner = loginAs("owner@example.com");
        add(owner, 1, 1);
        placeCod(owner, "563.95");
        Cookie other = loginAs("other@example.com");

        assertThat(listOrders(owner).size()).isEqualTo(1);
        assertThat(listOrders(other).size()).isZero();
    }

    @Test
    void aCancelledOrderStaysInTheListWithItsStatus() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        long orderId = placeCod(auth, "563.95");
        mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth)).andReturn();

        JsonNode orders = listOrders(auth);

        assertThat(orders.size()).isEqualTo(1);
        assertThat(orders.get(0).get("status").asString()).isEqualTo("CANCELLED");
    }

    @Test
    void listingOrdersNeedsALogin() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/orders")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    // ---- snapshots ---------------------------------------------------------------------------------------------------

    @Test
    void anOrderKeepsItsPricesWhenTheCataloguePriceAndStockChangeLater() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 2);
        // 998.00 + 49.90 + 40.00 = 1087.90
        long orderId = placeCod(auth, "1087.90");
        changeCatalogue("price = 650.00, stock_quantity = 0", "price = 499.00, stock_quantity = 25", 1);

        JsonNode order = body(mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(auth)).andReturn());
        JsonNode summary = listOrders(auth).get(0);

        assertThat(order.get("subtotal").asString()).isEqualTo("998.00");
        assertThat(order.get("payableTotal").asString()).isEqualTo("1087.90");
        assertThat(order.get("items").get(0).get("unitPrice").asString()).isEqualTo("499.00");
        assertThat(order.get("items").get(0).get("lineTotal").asString()).isEqualTo("998.00");
        assertThat(summary.get("payableTotal").asString()).isEqualTo("1087.90");
        assertThat(summary.get("items").get(0).get("unitPrice").asString()).isEqualTo("499.00");
    }

    @Test
    void anOrderCanBeOpenedByItsOwnerOnlyAndAnUnknownOrderIsNotFound() throws Exception {
        Cookie owner = loginAs("owner@example.com");
        add(owner, 1, 1);
        long orderId = placeCod(owner, "563.95");
        Cookie other = loginAs("other@example.com");

        assertThat(mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(owner)).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(other)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/v1/orders/987654").cookie(owner)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/v1/orders/" + orderId)).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    // ---- buyAgain ----------------------------------------------------------------------------------------------------

    @Test
    void buyAgainPutsEveryBookBackInTheBasketAtTheCurrentPriceAndLeavesTheOrderAlone() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 2);
        add(auth, 13, 1);
        // 998.00 + 99.00 = 1097.00, tax 54.85, delivery 40.00 => 1191.85
        long orderId = placeCod(auth, "1191.85");
        changeCatalogue("price = 550.00", "price = 499.00", 1);

        MvcResult result = buyAgain(auth, orderId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode response = body(result);
        assertThat(response.get("report").get("adjustments").size()).isZero();
        JsonNode lines = response.get("basket").get("items");
        assertThat(lines.size()).isEqualTo(2);
        JsonNode book1 = lines.get(0).get("book").get("id").asLong() == 1 ? lines.get(0) : lines.get(1);
        assertThat(book1.get("quantity").asInt()).isEqualTo(2);
        assertThat(book1.get("book").get("price").asString()).isEqualTo("550.00");
        assertThat(book1.get("lineTotal").asString()).isEqualTo("1100.00");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM order_item WHERE book_id = 1", java.math.BigDecimal.class))
                .isEqualByComparingTo("499.00");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PLACED");
    }

    @Test
    void buyAgainReportsABookThatIsOutOfStockAndDoesNotAddIt() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 6, 1);
        add(auth, 13, 1);
        // 899.00 + 99.00 = 998.00, tax 49.90, delivery 40.00 => 1087.90
        long orderId = placeCod(auth, "1087.90");
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = 6", Integer.class)).isZero();

        JsonNode response = body(buyAgain(auth, orderId));

        JsonNode adjustments = response.get("report").get("adjustments");
        assertThat(adjustments.size()).isEqualTo(1);
        assertThat(adjustments.get(0).get("bookId").asLong()).isEqualTo(6);
        assertThat(adjustments.get(0).get("reason").asString()).isEqualTo("UNAVAILABLE");
        assertThat(adjustments.get(0).get("addedQuantity").asInt()).isZero();
        assertThat(response.get("basket").get("items").size()).isEqualTo(1);
        assertThat(response.get("basket").get("items").get(0).get("book").get("id").asLong()).isEqualTo(13);
    }

    @Test
    void buyAgainReducesAQuantityAboveTheStockAndReportsIt() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 5);
        // 2495.00 + 124.75 + 40.00 = 2659.75
        long orderId = placeCod(auth, "2659.75");
        changeCatalogue("stock_quantity = 3", "stock_quantity = 25", 1);

        JsonNode response = body(buyAgain(auth, orderId));

        JsonNode adjustments = response.get("report").get("adjustments");
        assertThat(adjustments.size()).isEqualTo(1);
        assertThat(adjustments.get(0).get("reason").asString()).isEqualTo("QUANTITY_REDUCED");
        assertThat(adjustments.get(0).get("requestedQuantity").asInt()).isEqualTo(5);
        assertThat(adjustments.get(0).get("addedQuantity").asInt()).isEqualTo(3);
        assertThat(response.get("basket").get("items").get(0).get("quantity").asInt()).isEqualTo(3);
    }

    @Test
    void buyAgainReportsABookThatIsNoLongerListedWithItsRealTitle() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        add(auth, 2, 1);
        // 499.00 + 349.00 = 848.00, tax 42.40, delivery 40.00 => 930.40
        long orderId = placeCod(auth, "930.40");
        changeCatalogue("listed = FALSE", "listed = TRUE", 2);

        JsonNode response = body(buyAgain(auth, orderId));

        JsonNode adjustments = response.get("report").get("adjustments");
        assertThat(adjustments.size()).isEqualTo(1);
        assertThat(adjustments.get(0).get("bookId").asLong()).isEqualTo(2);
        assertThat(adjustments.get(0).get("reason").asString()).isEqualTo("NOT_IN_CATALOGUE");
        assertThat(adjustments.get(0).get("title").asString()).isEqualTo("Calm Mind, Clear Day");
        assertThat(response.get("basket").get("items").size()).isEqualTo(1);
    }

    @Test
    void buyAgainAddsToWhatIsAlreadyInTheBasketUpToTheTenUnitLimit() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 4);
        // 1996.00 + 99.80 + 40.00 = 2135.80
        long orderId = placeCod(auth, "2135.80");
        add(auth, 1, 8);

        JsonNode response = body(buyAgain(auth, orderId));

        assertThat(response.get("basket").get("items").get(0).get("quantity").asInt()).isEqualTo(10);
        assertThat(response.get("report").get("adjustments").get(0).get("reason").asString())
                .isEqualTo("QUANTITY_REDUCED");
    }

    @Test
    void buyAgainOfAnotherCustomersOrderIsNotFoundAndTheBasketStaysUntouched() throws Exception {
        Cookie owner = loginAs("owner@example.com");
        add(owner, 1, 1);
        long orderId = placeCod(owner, "563.95");
        Cookie other = loginAs("other@example.com");

        assertThat(buyAgain(other, orderId).getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM basket_item", Integer.class)).isZero();
        assertThat(mockMvc.perform(post("/api/v1/orders/" + orderId + "/buy-again")).andReturn().getResponse()
                .getStatus()).isEqualTo(401);
    }

    // ---- recommendations ---------------------------------------------------------------------------------------------

    @Test
    void aCustomerWithoutOrderHistoryGetsNoRecommendations() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        assertThat(recommendations(auth).size()).isZero();
    }

    @Test
    void recommendationsComeFromTheOrderedBooksCategoriesWithoutBasketBooksOrUnavailableOnes() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        placeCod(auth, "563.95");
        add(auth, 2, 1);

        JsonNode recommended = recommendations(auth);

        assertThat(recommended.size()).isBetween(1, 12);
        List<Long> ids = new ArrayList<>();
        recommended.forEach(book -> {
            ids.add(book.get("id").asLong());
            assertThat(book.get("available").asBoolean()).isTrue();
        });
        assertThat(ids).doesNotContain(2L).doesNotContain(5L).doesNotHaveDuplicates();
    }

    @Test
    void recommendationsNeedALogin() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/recommendations")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }
}
