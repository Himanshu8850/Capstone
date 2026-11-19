package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Reward points against the real database (T127, FR-RWD-01..06): a new account has 1,000 points and only its owner can
 * read it; the quote reports the balance and the cap; redeeming exactly the cap succeeds and one point more is refused
 * with nothing changed; redeemed points are deducted with the order and given back by a cancellation, once; points are
 * never earned; and the balance never goes below zero, also when the same customer's orders race.
 * Book 1 is a paperback at 499 (category 14, stock 25): 499.00 + 24.95 tax + 40.00 delivery = 563.95, so the cap is 281.
 */
class RewardRedemptionIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CSRF = "csrf-token-for-tests";
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

    private MvcResult account(Cookie auth) throws Exception {
        return mockMvc.perform(get("/api/v1/rewards/me").cookie(auth)).andReturn();
    }

    private int balanceOf(Cookie auth) throws Exception {
        MvcResult result = account(auth);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsString()).get("balance").asInt();
    }

    private MvcResult quote(Cookie auth, int points) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/checkout/quote"), auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pointsToRedeem\":" + points + "}")).andReturn();
    }

    private MvcResult place(Cookie auth, String method, int points, String expectedPayable) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders"), auth)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":" + ADDRESS + ",\"paymentMethod\":\"" + method + "\","
                        + (method.equals("COD") ? "" : "\"payment\":{\"card\":{\"number\":\"4111-1111-1111-1111\","
                                + "\"nameOnCard\":\"Asha Verma\",\"cvv\":\"123\",\"expiry\":\"12/2099\"}},")
                        + "\"pointsToRedeem\":" + points + ",\"expectedPayableTotal\":\"" + expectedPayable + "\"}"))
                .andReturn();
    }

    private long orderId(MvcResult placed) throws Exception {
        assertThat(placed.getResponse().getStatus()).as(placed.getResponse().getContentAsString()).isEqualTo(201);
        return JSON.readTree(placed.getResponse().getContentAsString()).get("id").asLong();
    }

    private List<String> ledger() {
        return jdbc.queryForList("SELECT type || ':' || points FROM reward_transaction ORDER BY id", String.class);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    // ---- getRewardAccount -------------------------------------------------------------------------------------------

    @Test
    void aNewAccountStartsWithOneThousandPoints() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");

        MvcResult result = account(auth);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("{\"balance\":1000}");
    }

    @Test
    void getRewardAccountReturnsOnlyTheCallersAccount() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie other = loginAs("other@example.com");
        jdbc.update("UPDATE reward_account SET balance = 250 WHERE customer_id = "
                + "(SELECT id FROM customer WHERE email = 'other@example.com')");

        assertThat(balanceOf(asha)).isEqualTo(1000);
        assertThat(balanceOf(other)).isEqualTo(250);
    }

    @Test
    void getRewardAccountNeedsALogin() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/rewards/me")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("UNAUTHENTICATED");
    }

    // ---- the quote and the cap --------------------------------------------------------------------------------------

    @Test
    void theQuoteShowsTheBalanceAndTheMaximumRedeemablePoints() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        JsonNode quote = body(quote(auth, 0));

        assertThat(quote.get("balance").asInt()).isEqualTo(1000);
        assertThat(quote.get("maxRedeemablePoints").asInt()).isEqualTo(281); // floor(50% of 563.95)
        assertThat(quote.get("payableTotal").asText()).isEqualTo("563.95");
    }

    @Test
    void theCapIsTheBalanceWhenTheBalanceIsLower() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        jdbc.update("UPDATE reward_account SET balance = 120");

        JsonNode quote = body(quote(auth, 120));

        assertThat(quote.get("maxRedeemablePoints").asInt()).isEqualTo(120);
        assertThat(quote.get("payableTotal").asText()).isEqualTo("443.95");
        assertThat(quote(auth, 121).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void aZeroBalanceAllowsNoRedemption() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        jdbc.update("UPDATE reward_account SET balance = 0");

        assertThat(body(quote(auth, 0)).get("maxRedeemablePoints").asInt()).isZero();
        MvcResult refused = quote(auth, 1);

        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(refused.getResponse().getContentAsString()).contains("POINTS_LIMIT_EXCEEDED")
                .contains("at most 0 points");
    }

    @Test
    void redeemingExactlyTheCapSucceedsAndOnePointMoreIsRefusedWithNothingChanged() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);

        MvcResult tooMany = place(auth, "COD", 282, "281.95");

        assertThat(tooMany.getResponse().getStatus()).isEqualTo(409);
        assertThat(tooMany.getResponse().getContentAsString()).contains("POINTS_LIMIT_EXCEEDED")
                .contains("at most 281 points");
        assertThat(balanceOf(auth)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = 1", Integer.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM basket_item", Integer.class)).isEqualTo(1);

        MvcResult exact = place(auth, "COD", 281, "282.95");

        assertThat(exact.getResponse().getStatus()).as(exact.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(body(exact).get("payableTotal").asText()).isEqualTo("282.95");
        assertThat(body(exact).get("pointsRedeemed").asInt()).isEqualTo(281);
        assertThat(balanceOf(auth)).isEqualTo(719);
        assertThat(ledger()).containsExactly("REDEEM:281");
    }

    // ---- deduct, restore, never earn --------------------------------------------------------------------------------

    @Test
    void redeemingThenCancellingGivesTheBalanceBackExactlyOnce() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        long orderId = orderId(place(auth, "CREDIT_CARD", 100, "463.95"));
        assertThat(balanceOf(auth)).isEqualTo(900);

        MvcResult cancelled = mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth))
                .andReturn();
        MvcResult again = mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth))
                .andReturn();

        assertThat(cancelled.getResponse().getStatus()).isEqualTo(200);
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(balanceOf(auth)).isEqualTo(1000);
        assertThat(ledger()).containsExactly("REDEEM:100", "RESTORE:100");
    }

    @Test
    void anOrderWithoutPointsLeavesTheBalanceAndTheLedgerAloneAndCancellingItAddsNothing() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        long orderId = orderId(place(auth, "COD", 0, "563.95"));

        assertThat(balanceOf(auth)).isEqualTo(1000);
        mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth)).andReturn();

        assertThat(balanceOf(auth)).isEqualTo(1000);
        assertThat(ledger()).isEmpty();
    }

    @Test
    void pointsAreNeverEarnedFromPurchases() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 2);
        orderId(place(auth, "COD", 0, "1087.90"));
        add(auth, 13, 1);
        orderId(place(auth, "CREDIT_CARD", 0, "103.95"));

        assertThat(balanceOf(auth)).isEqualTo(1000);
        assertThat(ledger()).isEmpty();
    }

    @Test
    void theBalanceFallsByEachOrdersRedeemedPointsAndNeverBelowZero() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        for (int i = 0; i < 3; i++) {
            add(auth, 1, 1);
            orderId(place(auth, "COD", 281, "282.95"));
        }
        assertThat(balanceOf(auth)).isEqualTo(157); // 1000 - 3 x 281

        add(auth, 1, 1);
        MvcResult over = place(auth, "COD", 158, "405.95");

        assertThat(over.getResponse().getStatus()).isEqualTo(409);
        assertThat(over.getResponse().getContentAsString()).contains("POINTS_LIMIT_EXCEEDED")
                .contains("at most 157 points");
        assertThat(balanceOf(auth)).isEqualTo(157);

        orderId(place(auth, "COD", 157, "406.95"));
        assertThat(balanceOf(auth)).isZero();
    }

    @Test
    void theDatabaseRefusesANegativeBalance() throws Exception {
        loginAs("asha.verma@example.com");

        assertThatThrownBy(() -> jdbc.update("UPDATE reward_account SET balance = -1"))
                .hasMessageContaining("reward_account");
        assertThat(jdbc.queryForObject("SELECT balance FROM reward_account", Integer.class)).isEqualTo(1000);
    }

    // ---- concurrency ------------------------------------------------------------------------------------------------

    @Test
    void ordersRacingOnOneAccountNeverTakeTheBalanceBelowZero() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 1);
        jdbc.update("UPDATE reward_account SET balance = 300");
        int racers = 5;
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
                    return place(auth, "COD", 281, "282.95").getResponse().getStatus();
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

        // One basket and 300 points can pay for one order of 281 points: the other requests find the basket empty.
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(racers - 1);
        assertThat(balanceOf(auth)).isEqualTo(19);
        assertThat(ledger()).containsExactly("REDEEM:281");
        assertThat(jdbc.queryForObject("SELECT min(balance) FROM reward_account", Integer.class)).isNotNegative();
    }
}
