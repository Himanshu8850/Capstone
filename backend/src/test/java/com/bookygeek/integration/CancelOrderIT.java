package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Cancelling an order against the real database (T117, FR-ORD-04..06): the stock, the redeemed points and the payment
 * are all restored in the one transaction that cancels the order, the {@code RESTORE} ledger row exists only when points
 * were redeemed, the window (strictly under 48 hours, cash on delivery included) and the repeat cancel are 409, another
 * customer's or an unknown order is 404, the database refuses a second {@code RESTORE} for an order and a
 * {@code CANCELLED} order without a cancellation time, and two cancels racing for one order yield exactly one success.
 * Book 1 is a paperback at 499 with stock 25 and book 13 an eBook at 99.
 */
class CancelOrderIT extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";
    private static final String CARD = "{\"card\":{\"number\":\"4111-1111-1111-1111\",\"nameOnCard\":\"Asha Verma\","
            + "\"cvv\":\"123\",\"expiry\":\"12/2099\"}}";
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

    /** Places the order and returns its id; the basket must already hold the books. */
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

    /** 2 x book 1 paid by card with 100 points: 998.00 + 49.90 + 40.00 = 1087.90, payable 987.90. */
    private long cardOrderWithPoints(Cookie auth) throws Exception {
        add(auth, 1, 2);
        return place(auth, "CREDIT_CARD", CARD, 100, "987.90");
    }

    private MvcResult cancel(Cookie auth, long orderId) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth)).andReturn();
    }

    private void placedHoursAgo(long orderId, String interval) {
        jdbc.update("UPDATE orders SET placed_at = now() - CAST(? AS interval) WHERE id = ?", interval, orderId);
    }

    private int balance() {
        return jdbc.queryForObject("SELECT balance FROM reward_account WHERE customer_id = (SELECT customer_id FROM orders ORDER BY id LIMIT 1)", Integer.class);
    }

    private int stock(long bookId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = ?", Integer.class, bookId);
    }

    private String paymentStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM payment WHERE order_id = ?", String.class, orderId);
    }

    private String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private List<String> ledger() {
        return jdbc.queryForList("SELECT type || ':' || points FROM reward_transaction ORDER BY id", String.class);
    }

    // ---- the restore ------------------------------------------------------------------------------------------------

    @Test
    void cancellingACardOrderRefundsItAndRestoresStockAndPointsTogether() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        assertThat(stock(1)).isEqualTo(23);
        assertThat(balance()).isEqualTo(900);

        MvcResult result = cancel(auth, orderId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"CANCELLED\"")
                .contains("\"paymentStatus\":\"REFUNDED\"").contains("\"cancellable\":false")
                .contains("\"cancellableUntil\":null").contains("\"pointsRedeemed\":100");
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancelled_at IS NOT NULL FROM orders WHERE id = ?", Boolean.class, orderId))
                .isTrue();
        assertThat(stock(1)).isEqualTo(25);
        assertThat(balance()).isEqualTo(1000);
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(ledger()).containsExactly("REDEEM:100", "RESTORE:100");
    }

    @Test
    void aCashOnDeliveryOrderWithoutPointsHasItsPaymentCancelledAndNoLedgerRow() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        long orderId = place(auth, "COD", null, 0, "563.95");
        assertThat(paymentStatus(orderId)).isEqualTo("PENDING_ON_DELIVERY");

        MvcResult result = cancel(auth, orderId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"paymentStatus\":\"CANCELLED\"");
        assertThat(paymentStatus(orderId)).isEqualTo("CANCELLED");
        assertThat(stock(1)).isEqualTo(25);
        assertThat(balance()).isEqualTo(1000);
        assertThat(ledger()).isEmpty();
    }

    @Test
    void anEBookIsNeverCountedInStockWhenAMixedOrderIsCancelled() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        add(auth, 13, 1);
        // 499 + 99 = 598.00, tax 29.90, delivery 40.00 => 667.90
        long orderId = place(auth, "COD", null, 0, "667.90");
        assertThat(stock(1)).isEqualTo(24);

        cancel(auth, orderId);

        assertThat(stock(1)).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = 13", Integer.class)).isNull();
    }

    @Test
    void anEBookOnlyOrderCancelsWithoutTouchingAnyStock() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 13, 1);
        long orderId = place(auth, "UPI", "{\"upi\":{\"upiId\":\"asha@okbank\"}}", 0, "103.95");

        MvcResult result = cancel(auth, orderId);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = 13", Integer.class)).isNull();
    }

    // ---- the window and repeat cancels -------------------------------------------------------------------------------

    @Test
    void anOrderJustInsideTheFortyEightHourWindowCanStillBeCancelled() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        placedHoursAgo(orderId, "47 hours 55 minutes");

        assertThat(cancel(auth, orderId).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anOrderPastTheWindowIsRejectedAndNothingChanges() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        placedHoursAgo(orderId, "48 hours 1 minute");

        MvcResult result = cancel(auth, orderId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("CANCELLATION_WINDOW_EXPIRED");
        assertNothingWasChanged(orderId);
    }

    @Test
    void theWindowAlsoAppliesToACashOnDeliveryOrder() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        long orderId = place(auth, "COD", null, 0, "563.95");
        placedHoursAgo(orderId, "49 hours");

        MvcResult result = cancel(auth, orderId);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("CANCELLATION_WINDOW_EXPIRED");
        assertThat(paymentStatus(orderId)).isEqualTo("PENDING_ON_DELIVERY");
        assertThat(stock(1)).isEqualTo(24);
    }

    @Test
    void anOrderThatIsOutsideTheWindowIsNoLongerReportedAsCancellable() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        placedHoursAgo(orderId, "72 hours");

        MvcResult result = mockMvc.perform(authed(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/orders/" + orderId), auth))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"cancellable\":false")
                .contains("\"status\":\"PLACED\"");
    }

    @Test
    void cancellingTwiceReportsAlreadyCancelledAndRestoresNothingTwice() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        assertThat(cancel(auth, orderId).getResponse().getStatus()).isEqualTo(200);

        MvcResult again = cancel(auth, orderId);

        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(again.getResponse().getContentAsString()).contains("ALREADY_CANCELLED");
        assertThat(stock(1)).isEqualTo(25);
        assertThat(balance()).isEqualTo(1000);
        assertThat(ledger()).containsExactly("REDEEM:100", "RESTORE:100");
    }

    @Test
    void aCancelledOrderStaysCancelledEvenWhenItsWindowHasAlsoPassed() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        cancel(auth, orderId);
        placedHoursAgo(orderId, "100 hours");

        MvcResult again = cancel(auth, orderId);

        assertThat(again.getResponse().getContentAsString()).contains("ALREADY_CANCELLED");
    }

    // ---- ownership, authentication -----------------------------------------------------------------------------------

    @Test
    void anotherCustomersOrderAndAnUnknownOrderAreNotFound() throws Exception {
        Cookie owner = loginAs("owner@example.com");
        long orderId = cardOrderWithPoints(owner);
        Cookie other = loginAs("other@example.com");

        MvcResult foreign = cancel(other, orderId);
        MvcResult unknown = cancel(other, 987654);

        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(404);
        assertNothingWasChanged(orderId);
    }

    @Test
    void cancellingWithoutLoggingInIsUnauthenticated() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);

        MvcResult result = mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancellation")
                .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertNothingWasChanged(orderId);
    }

    @Test
    void cancellingWithoutTheCsrfTokenIsRejectedAsUnauthenticated() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);

        MvcResult result = mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancellation").cookie(auth)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertNothingWasChanged(orderId);
    }

    // ---- one transaction ---------------------------------------------------------------------------------------------

    @Test
    void aFailureWhileSettlingThePaymentRollsBackTheWholeCancellation() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        // A payment that is already REFUNDED cannot be settled again, so the last step of the cancellation fails
        // after the order, the stock and the points were already changed in the same transaction.
        jdbc.update("UPDATE payment SET status = 'REFUNDED' WHERE order_id = ?", orderId);

        MvcResult result = cancel(auth, orderId);

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(orderStatus(orderId)).isEqualTo("PLACED");
        assertThat(jdbc.queryForObject("SELECT cancelled_at IS NULL FROM orders WHERE id = ?", Boolean.class, orderId))
                .isTrue();
        assertThat(stock(1)).isEqualTo(23);
        assertThat(balance()).isEqualTo(900);
        assertThat(ledger()).containsExactly("REDEEM:100");
    }

    // ---- database constraints ----------------------------------------------------------------------------------------

    @Test
    void theDatabaseRefusesASecondRestoreRowForTheSameOrder() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        cancel(auth, orderId);
        long customerId = jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = ?", Long.class, orderId);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO reward_transaction (customer_id, order_id, type, points, "
                + "created_at) VALUES (?, ?, 'RESTORE', 100, now())", customerId, orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(ledger()).containsExactly("REDEEM:100", "RESTORE:100");
    }

    @Test
    void theDatabaseRefusesACancelledOrderWithoutACancellationTimeAndTheOtherWayRound() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET cancelled_at = now() WHERE id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(orderStatus(orderId)).isEqualTo("PLACED");
    }

    // ---- concurrency -------------------------------------------------------------------------------------------------

    @Test
    void twoCancelsRacingForTheSameOrderYieldExactlyOneSuccessAndOneRestore() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long orderId = cardOrderWithPoints(auth);
        int racers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch ready = new CountDownLatch(racers);
        CountDownLatch go = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return cancel(auth, orderId).getResponse().getStatus();
                };
                results.add(pool.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(racers - 1);
        assertThat(stock(1)).isEqualTo(25);
        assertThat(balance()).isEqualTo(1000);
        assertThat(ledger()).containsExactly("REDEEM:100", "RESTORE:100");
        assertThat(paymentStatus(orderId)).isEqualTo("REFUNDED");
    }

    private void assertNothingWasChanged(long orderId) {
        assertThat(orderStatus(orderId)).isEqualTo("PLACED");
        assertThat(paymentStatus(orderId)).isEqualTo("PAID");
        assertThat(stock(1)).isEqualTo(23);
        assertThat(balance()).isEqualTo(900);
        assertThat(ledger()).containsExactly("REDEEM:100");
    }
}
