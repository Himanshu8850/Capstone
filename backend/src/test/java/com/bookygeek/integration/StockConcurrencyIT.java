package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Concurrent checkouts never oversell (T100, FR-ORD-08): the ordered physical books are locked
 * ({@code SELECT ... FOR UPDATE}) and re-checked inside the order transaction, so with several customers racing for
 * the last copy exactly one wins, and stock never goes below zero. Real, committed transactions are used (the test
 * base class is not transactional). Book 6 is a hardcover with stock 1 at 899.00; book 1 a paperback with stock 25
 * at 499.00.
 */
class StockConcurrencyIT extends IntegrationTestBase {

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

    private MvcResult place(Cookie auth, String expectedPayable) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders"), auth).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"address\":" + ADDRESS
                        + ",\"paymentMethod\":\"COD\",\"pointsToRedeem\":0,\"expectedPayableTotal\":\""
                        + expectedPayable + "\"}")).andReturn();
    }

    /** Places one order per customer at the same moment and returns the HTTP statuses. */
    private List<Integer> raceToPlace(List<Cookie> customers, String expectedPayable) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(customers.size());
        CountDownLatch ready = new CountDownLatch(customers.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Cookie customer : customers) {
                Callable<Integer> task = () -> {
                    ready.countDown();
                    go.await();
                    return place(customer, expectedPayable).getResponse().getStatus();
                };
                results.add(pool.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Cookie> customersWithBasket(int count, long bookId, int quantity) throws Exception {
        List<Cookie> customers = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Cookie auth = loginAs("buyer" + i + "@example.com");
            add(auth, bookId, quantity);
            customers.add(auth);
        }
        return customers;
    }

    private int stock(long bookId) {
        return jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = ?", Integer.class, bookId);
    }

    @Test
    void sixCustomersRacingForTheLastCopyYieldExactlyOneOrder() throws Exception {
        List<Cookie> customers = customersWithBasket(6, 6, 1);

        // 899.00 + 44.95 tax + 40.00 delivery = 983.95
        List<Integer> statuses = raceToPlace(customers, "983.95");

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        assertThat(stock(6)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment", Integer.class)).isEqualTo(1);
        // The five losers keep their baskets; the winner's basket is empty.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM basket_item", Integer.class)).isEqualTo(5);
    }

    @Test
    void theLoserIsToldTheBookIsOutOfStock() throws Exception {
        List<Cookie> customers = customersWithBasket(2, 6, 1);

        List<Integer> statuses = raceToPlace(customers, "983.95");

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        Cookie loser = customers.get(statuses.get(0) == 201 ? 1 : 0);
        MvcResult again = place(loser, "983.95");
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(again.getResponse().getContentAsString()).contains("OUT_OF_STOCK");
    }

    @Test
    void ordersForTheSameBooksInReversedBasketOrderNeverDeadlock() throws Exception {
        // Books 1 and 2 are added in opposite orders; the books are locked in ascending id order either way.
        List<Cookie> customers = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Cookie auth = loginAs("buyer" + i + "@example.com");
            add(auth, i % 2 == 0 ? 1 : 2, 1);
            add(auth, i % 2 == 0 ? 2 : 1, 1);
            customers.add(auth);
        }
        // 499 + 349 = 848.00, tax 42.40, delivery 40.00 => 930.40
        List<Integer> statuses = raceToPlace(customers, "930.40");

        assertThat(statuses).allMatch(s -> s == 201);
        assertThat(stock(1)).isEqualTo(19);
        assertThat(stock(2)).isEqualTo(34);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(6);
    }

    @Test
    void stockIsNeverOversoldWhenSeveralOrdersEachTakeSeveralCopies() throws Exception {
        // 25 copies, 10 per order: exactly two orders fit and the stock ends at 5.
        List<Cookie> customers = customersWithBasket(6, 1, 10);

        // 4990.00 + 249.50 tax + 40.00 delivery = 5279.50
        List<Integer> statuses = raceToPlace(customers, "5279.50");

        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(2);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(4);
        assertThat(stock(1)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(2);
    }
}
