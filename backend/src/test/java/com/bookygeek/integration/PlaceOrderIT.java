package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Checkout and order placement against the real database (T099): the totals, the 5-point-redemption rule and the
 * all-or-nothing transaction (stock, points, basket, payment row), idempotent replay, every 409, the payment
 * simulator, address validation and ownership, and that card details are never stored.
 * Book 1 is a paperback at 499 with stock 25, book 6 a hardcover with stock 1, book 13 an eBook at 99 and book 5 a
 * paperback with no stock. The basket of 1 x book 1 prices to 499.00 + 24.95 tax + 40.00 delivery = 563.95.
 */
class PlaceOrderIT extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";
    private static final String CARD = "{\"number\":\"4111-1111-1111-1111\",\"nameOnCard\":\"Asha Verma\","
            + "\"cvv\":\"123\",\"expiry\":\"12/2099\"}";
    private static final String ADDRESS = "{\"fullName\":\"Asha Verma\",\"phone\":\"9876543210\","
            + "\"addressLine1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pinCode\":\"560001\"}";

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

    private long saveAddress(Cookie auth) throws Exception {
        MvcResult created = mockMvc.perform(authed(post("/api/v1/addresses"), auth)
                .contentType(MediaType.APPLICATION_JSON).content(ADDRESS)).andReturn();
        String body = created.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private MvcResult place(Cookie auth, UUID key, String body) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders"), auth).header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private static String order(String address, String method, String payment, int points, String expected) {
        return "{" + address + "\"paymentMethod\":\"" + method + "\","
                + (payment == null ? "" : "\"payment\":" + payment + ",")
                + "\"pointsToRedeem\":" + points + ",\"expectedPayableTotal\":\"" + expected + "\"}";
    }

    private static String saved(long id) {
        return "\"addressId\":" + id + ",";
    }

    private static String newAddress() {
        return "\"address\":" + ADDRESS + ",";
    }

    private static String cardPayment() {
        return "{\"card\":" + CARD + "}";
    }

    private int balance() {
        return jdbc.queryForObject("SELECT balance FROM reward_account", Integer.class);
    }

    private int stock(long bookId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = ?", Integer.class, bookId);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    // ---- quote ----------------------------------------------------------------------------------------------------

    @Test
    void theQuotePricesTheBasketAndStoresNothing() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        MvcResult quote = mockMvc.perform(authed(post("/api/v1/checkout/quote"), auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pointsToRedeem\":100}")).andReturn();

        assertThat(quote.getResponse().getStatus()).isEqualTo(200);
        assertThat(quote.getResponse().getContentAsString()).contains("\"subtotal\":\"499.00\"")
                .contains("\"tax\":\"24.95\"").contains("\"deliveryCharge\":\"40.00\"")
                .contains("\"total\":\"563.95\"").contains("\"balance\":1000").contains("\"maxRedeemablePoints\":281")
                .contains("\"pointsToRedeem\":100").contains("\"payableTotal\":\"463.95\"").contains("\"issues\":[]");
        assertThat(count("orders")).isZero();
        assertThat(balance()).isEqualTo(1000);
    }

    @Test
    void theQuoteRefusesMorePointsThanTheCapAndAnEmptyBasket() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        MvcResult empty = mockMvc.perform(authed(post("/api/v1/checkout/quote"), auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pointsToRedeem\":0}")).andReturn();
        assertThat(empty.getResponse().getStatus()).isEqualTo(409);
        assertThat(empty.getResponse().getContentAsString()).contains("EMPTY_BASKET");

        add(auth, 1, 1);
        MvcResult over = mockMvc.perform(authed(post("/api/v1/checkout/quote"), auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pointsToRedeem\":282}")).andReturn();
        assertThat(over.getResponse().getStatus()).isEqualTo(409);
        assertThat(over.getResponse().getContentAsString()).contains("POINTS_LIMIT_EXCEEDED")
                .contains("at most 281 points");
    }

    @Test
    void theQuoteReportsABasketLineThatNoLongerHasStock() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 3);
        jdbc.update("UPDATE book SET stock_quantity = 2 WHERE id = 1");

        MvcResult quote = mockMvc.perform(authed(post("/api/v1/checkout/quote"), auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"pointsToRedeem\":0}")).andReturn();

        assertThat(quote.getResponse().getContentAsString()).contains("\"type\":\"QUANTITY_REDUCED\"");
    }

    // ---- placing an order -----------------------------------------------------------------------------------------

    @Test
    void aCardOrderIsPaidTakesStockDeductsPointsAndClearsTheBasket() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long addressId = saveAddress(auth);
        add(auth, 1, 2);

        // 2 x 499 = 998.00, tax 49.90, delivery 40.00 => 1087.90; 100 points => 987.90
        MvcResult result = place(auth, UUID.randomUUID(), order(saved(addressId), "CREDIT_CARD", cardPayment(), 100,
                "987.90"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).startsWith("/api/v1/orders/");
        assertThat(result.getResponse().getContentAsString()).contains("\"reference\":\"BG-000001\"")
                .contains("\"status\":\"PLACED\"").contains("\"paymentStatus\":\"PAID\"")
                .contains("\"payableTotal\":\"987.90\"").contains("\"pointsRedeemed\":100")
                .contains("\"cancellable\":true");
        assertThat(stock(1)).isEqualTo(23);
        assertThat(balance()).isEqualTo(900);
        assertThat(count("basket_item")).isZero();
        assertThat(count("order_item")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM payment", String.class)).isEqualTo("PAID");
        assertThat(jdbc.queryForList("SELECT type || ':' || points FROM reward_transaction", String.class))
                .containsExactly("REDEEM:100");
    }

    @Test
    void anEBookOnlyOrderHasNoDeliveryChargeAndTakesNoStock() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 13, 1);

        // 99.00 + 4.95 tax + 0.00 delivery = 103.95
        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "UPI", "{\"upi\":{\"upiId\":\"asha@okbank\"}}",
                0, "103.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"deliveryCharge\":\"0.00\"");
        assertThat(balance()).isEqualTo(1000);
        assertThat(count("reward_transaction")).isZero();
    }

    @Test
    void anEBookOnlyOrderNeedsNoAddressAndKeepsNone() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 13, 1);

        MvcResult result = place(auth, UUID.randomUUID(), order("", "UPI", "{\"upi\":{\"upiId\":\"asha@okbank\"}}",
                0, "103.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"address\":null");
        assertThat(jdbc.queryForObject("SELECT ship_line1 FROM orders", String.class)).isNull();
        String location = result.getResponse().getHeader("Location");
        MvcResult read = mockMvc.perform(authed(get(location), auth)).andReturn();
        assertThat(read.getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(authed(get("/api/v1/orders"), auth)).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void aBasketWithAPhysicalBookStillNeedsAnAddress() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        add(auth, 13, 1);

        MvcResult result = place(auth, UUID.randomUUID(), order("", "COD", null, 0, "667.90"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(count("orders")).isZero();
    }

    @Test
    void cashOnDeliveryIsPendingAndNeedsNoPaymentDetails() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 0, "563.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("\"paymentStatus\":\"PENDING_ON_DELIVERY\"");
    }

    @Test
    void theOrderKeepsASnapshotOfTheAddressAndThePrices() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long addressId = saveAddress(auth);
        add(auth, 1, 1);
        MvcResult placed = place(auth, UUID.randomUUID(), order(saved(addressId), "COD", null, 0, "563.95"));
        String id = placed.getResponse().getHeader("Location").substring("/api/v1/orders/".length());

        jdbc.update("UPDATE address SET city = 'Mysuru'");
        MvcResult fetched;
        try {
            jdbc.update("UPDATE book SET price = 999 WHERE id = 1");
            fetched = mockMvc.perform(get("/api/v1/orders/" + id).cookie(auth)).andReturn();
        } finally {
            jdbc.update("UPDATE book SET price = 499 WHERE id = 1");
        }

        assertThat(fetched.getResponse().getStatus()).isEqualTo(200);
        assertThat(fetched.getResponse().getContentAsString()).contains("\"city\":\"Bengaluru\"")
                .contains("\"unitPrice\":\"499.00\"").contains("\"payableTotal\":\"563.95\"");
    }

    // ---- idempotency ----------------------------------------------------------------------------------------------

    @Test
    void theSameKeyReplaysTheStoredOrderWithoutAnotherOrderOrStockMovement() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        UUID key = UUID.randomUUID();
        String body = order(newAddress(), "CREDIT_CARD", cardPayment(), 50, "513.95");

        MvcResult first = place(auth, key, body);
        MvcResult second = place(auth, key, body);

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(count("orders")).isEqualTo(1);
        assertThat(stock(1)).isEqualTo(24);
        assertThat(balance()).isEqualTo(950);
    }

    @Test
    void aMissingOrMalformedIdempotencyKeyIsRejected() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        String body = order(newAddress(), "COD", null, 0, "563.95");

        MvcResult missing = mockMvc.perform(authed(post("/api/v1/orders"), auth)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        MvcResult malformed = mockMvc.perform(authed(post("/api/v1/orders"), auth).header("Idempotency-Key", "nope")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();

        assertThat(missing.getResponse().getStatus()).isEqualTo(400);
        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
        assertThat(count("orders")).isZero();
    }

    // ---- the payment simulator ------------------------------------------------------------------------------------

    @Test
    void aDeclinedCardChangesNothingButLeavesAFailedAttempt() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        String declined = "{\"card\":{\"number\":\"4000-0000-0000-0002\",\"nameOnCard\":\"Asha Verma\","
                + "\"cvv\":\"123\",\"expiry\":\"12/2099\"}}";

        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "DEBIT_CARD", declined, 100, "463.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("PAYMENT_DECLINED");
        assertThat(count("orders")).isZero();
        assertThat(count("basket_item")).isEqualTo(1);
        assertThat(stock(1)).isEqualTo(25);
        assertThat(balance()).isEqualTo(1000);
        assertThat(count("reward_transaction")).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM payment", String.class)).containsExactly("FAILED");
    }

    @Test
    void aDeclinedUpiIdFailsAndRetryingWithAGoodCardSucceeds() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        MvcResult declined = place(auth, UUID.randomUUID(), order(newAddress(), "UPI",
                "{\"upi\":{\"upiId\":\"failure@upi\"}}", 0, "563.95"));
        MvcResult retry = place(auth, UUID.randomUUID(), order(newAddress(), "CREDIT_CARD", cardPayment(), 0, "563.95"));

        assertThat(declined.getResponse().getStatus()).isEqualTo(409);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForList("SELECT status FROM payment ORDER BY id", String.class))
                .containsExactly("FAILED", "PAID");
    }

    @Test
    void repeatingADeclinedKeyIsDeclinedAgainWithoutAnotherAttemptRow() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        UUID key = UUID.randomUUID();
        String body = order(newAddress(), "UPI", "{\"upi\":{\"upiId\":\"failure@upi\"}}", 0, "563.95");

        place(auth, key, body);
        MvcResult again = place(auth, key, body);

        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(again.getResponse().getContentAsString()).contains("PAYMENT_DECLINED");
        assertThat(count("payment")).isEqualTo(1);
    }

    @Test
    void anExpiredCardAndMismatchedPaymentDetailsAreValidationErrors() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        String expired = "{\"card\":{\"number\":\"4111-1111-1111-1111\",\"nameOnCard\":\"Asha Verma\","
                + "\"cvv\":\"123\",\"expiry\":\"01/2020\"}}";

        MvcResult expiredResult = place(auth, UUID.randomUUID(),
                order(newAddress(), "CREDIT_CARD", expired, 0, "563.95"));
        MvcResult cardForUpi = place(auth, UUID.randomUUID(), order(newAddress(), "UPI", cardPayment(), 0, "563.95"));
        MvcResult codWithCard = place(auth, UUID.randomUUID(), order(newAddress(), "COD", cardPayment(), 0, "563.95"));
        MvcResult noPayment = place(auth, UUID.randomUUID(), order(newAddress(), "CREDIT_CARD", null, 0, "563.95"));

        assertThat(expiredResult.getResponse().getStatus()).isEqualTo(400);
        assertThat(expiredResult.getResponse().getContentAsString()).contains("payment.card.expiry");
        assertThat(cardForUpi.getResponse().getStatus()).isEqualTo(400);
        assertThat(codWithCard.getResponse().getStatus()).isEqualTo(400);
        assertThat(noPayment.getResponse().getStatus()).isEqualTo(400);
        assertThat(count("payment")).isZero();
    }

    @Test
    void cardDetailsAreNeverStoredOrEchoed() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "CREDIT_CARD", cardPayment(), 0, "563.95"));

        assertThat(result.getResponse().getContentAsString()).doesNotContain("4111").doesNotContain("cvv");
        assertThat(jdbc.queryForList("SELECT payment::text FROM payment", String.class))
                .allSatisfy(row -> assertThat(row).doesNotContain("4111"));
    }

    // ---- conflicts ------------------------------------------------------------------------------------------------

    @Test
    void aWrongExpectedTotalIsABasketChangedConflictAndNothingChanges() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 0, "500.00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("BASKET_CHANGED");
        assertThat(count("orders")).isZero();
        assertThat(count("payment")).isZero();
        assertThat(count("basket_item")).isEqualTo(1);
    }

    @Test
    void aPriceChangeBetweenQuoteAndPlacementIsABasketChangedConflict() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        MvcResult result;
        try {
            jdbc.update("UPDATE book SET price = 600 WHERE id = 1");
            result = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 0, "563.95"));
        } finally {
            jdbc.update("UPDATE book SET price = 499 WHERE id = 1");
        }

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("BASKET_CHANGED");
    }

    @Test
    void anEmptyBasketIsAConflict() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");

        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 0, "563.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("EMPTY_BASKET");
    }

    @Test
    void stockThatDroppedBelowTheBasketQuantityIsOutOfStockAndNothingChanges() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 3);
        jdbc.update("UPDATE book SET stock_quantity = 2 WHERE id = 1");

        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 0, "1611.85"));

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("OUT_OF_STOCK").contains("Only 2 copies");
        assertThat(stock(1)).isEqualTo(2);
        assertThat(count("orders")).isZero();
        assertThat(count("basket_item")).isEqualTo(1);
    }

    @Test
    void redeemingMoreThanHalfTheOrderOrMoreThanTheBalanceIsRefused() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        MvcResult overCap = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 282, "281.95"));
        assertThat(overCap.getResponse().getStatus()).isEqualTo(409);
        assertThat(overCap.getResponse().getContentAsString()).contains("POINTS_LIMIT_EXCEEDED");

        jdbc.update("UPDATE reward_account SET balance = 10");
        MvcResult overBalance = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 11, "552.95"));
        assertThat(overBalance.getResponse().getStatus()).isEqualTo(409);
        assertThat(overBalance.getResponse().getContentAsString()).contains("POINTS_LIMIT_EXCEEDED");
        assertThat(count("orders")).isZero();
        assertThat(balance()).isEqualTo(10);
    }

    @Test
    void theHalfCapIsInclusive() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        MvcResult result = place(auth, UUID.randomUUID(), order(newAddress(), "COD", null, 281, "282.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(balance()).isEqualTo(719);
    }

    // ---- request shape and ownership ------------------------------------------------------------------------------

    @Test
    void exactlyOneAddressSourceIsRequired() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long addressId = saveAddress(auth);
        add(auth, 1, 1);

        MvcResult both = place(auth, UUID.randomUUID(),
                order(saved(addressId) + newAddress(), "COD", null, 0, "563.95"));
        MvcResult neither = place(auth, UUID.randomUUID(), order("", "COD", null, 0, "563.95"));

        assertThat(both.getResponse().getStatus()).isEqualTo(400);
        assertThat(neither.getResponse().getStatus()).isEqualTo(400);
        assertThat(count("orders")).isZero();
    }

    @Test
    void anotherCustomersAddressCannotBeUsed() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        long ashasAddress = saveAddress(asha);
        add(ravi, 1, 1);

        MvcResult result = place(ravi, UUID.randomUUID(), order(saved(ashasAddress), "COD", null, 0, "563.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(count("orders")).isZero();
        assertThat(count("basket_item")).isEqualTo(1);
    }

    @Test
    void aNewAddressWithAnUnknownIndianStateIsRejected() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        String address = "\"address\":" + ADDRESS.replace("Karnataka", "Atlantis") + ",";

        MvcResult result = place(auth, UUID.randomUUID(), order(address, "COD", null, 0, "563.95"));

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void placingAndQuotingNeedALoginAndTheCsrfHeader() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        String body = order(newAddress(), "COD", null, 0, "563.95");

        MvcResult anonymous = mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        MvcResult noCsrf = mockMvc.perform(post("/api/v1/orders").cookie(auth)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
        MvcResult quoteAnonymous = mockMvc.perform(post("/api/v1/checkout/quote")
                .contentType(MediaType.APPLICATION_JSON).content("{\"pointsToRedeem\":0}")).andReturn();

        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(noCsrf.getResponse().getStatus()).isEqualTo(401);
        assertThat(quoteAnonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(count("orders")).isZero();
    }

    // ---- getOrder -------------------------------------------------------------------------------------------------

    @Test
    void anOrderCanOnlyBeReadByItsOwner() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        add(asha, 1, 1);
        MvcResult placed = place(asha, UUID.randomUUID(), order(newAddress(), "COD", null, 0, "563.95"));
        String id = placed.getResponse().getHeader("Location").substring("/api/v1/orders/".length());

        assertThat(mockMvc.perform(get("/api/v1/orders/" + id).cookie(asha)).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/v1/orders/" + id).cookie(ravi)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mockMvc.perform(get("/api/v1/orders/" + id)).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mockMvc.perform(get("/api/v1/orders/999999").cookie(asha)).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    @Test
    void theDatabaseRefusesAnOrderBeyondTheRewardCap() throws Exception {
        loginAs("asha.verma@example.com");
        Long id = jdbc.queryForObject("SELECT id FROM customer", Long.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO orders (customer_id, status, placed_at, ship_full_name, ship_phone, ship_line1, ship_city, "
                        + "ship_state, ship_pin_code, ship_country, subtotal, tax, delivery_charge, points_redeemed, "
                        + "payable_total) VALUES (?, 'PLACED', now(), 'A', '9876543210', 'x', 'c', 's', '560001', "
                        + "'India', 100, 5, 40, 100, 45)", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
