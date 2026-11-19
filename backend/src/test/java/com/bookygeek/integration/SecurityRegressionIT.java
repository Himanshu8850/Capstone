package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static com.bookygeek.testsupport.TestCredentials.WRONG_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Security regression suite (T138) over the whole API against the real database:
 * <ul>
 *   <li>authorisation: one customer can never read or change another customer's addresses, basket, orders or points
 *       (404 {@code NOT_FOUND}; a foreign {@code addressId} at placement is 400 {@code VALIDATION_FAILED}) and nothing
 *       is changed;</li>
 *   <li>every protected operation without the {@code BG_AUTH} cookie, and every state-changing one without a matching
 *       {@code X-XSRF-TOKEN}, is the contract's 401 {@code UNAUTHENTICATED} {@code ApiError} and changes nothing;</li>
 *   <li>error bodies carry no stack trace, class, SQL or table name, and injection text in a search is inert;</li>
 *   <li>the log output of failed logins, declined and successful card payments and a forced 500 contains neither the
 *       password, the card number, the CVV, the session token nor the signing secret;</li>
 *   <li>passwords are stored only as salted {@code {pbkdf2}} hashes.</li>
 * </ul>
 * Book 1 is a paperback at 499 with stock 25: one copy costs 499.00 + 24.95 + 40.00 = 563.95.
 */
@ExtendWith(OutputCaptureExtension.class)
class SecurityRegressionIT extends IntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CSRF = "csrf-token-for-tests";
    private static final String CARD_NUMBER = "4111-1111-1111-1111";
    private static final String CARD = "{\"card\":{\"number\":\"" + CARD_NUMBER + "\",\"nameOnCard\":\"Asha Verma\","
            + "\"cvv\":\"123\",\"expiry\":\"12/2099\"}}";
    private static final String DECLINED_CARD = "{\"card\":{\"number\":\"4000-0000-0000-0002\","
            + "\"nameOnCard\":\"Asha Verma\",\"cvv\":\"123\",\"expiry\":\"12/2099\"}}";
    private static final String ADDRESS = "{\"fullName\":\"Asha Verma\",\"phone\":\"9876543210\","
            + "\"addressLine1\":\"12 MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pinCode\":\"560001\"}";

    /** One operation of the contract that needs the session cookie; {@code mutating} ones also need the CSRF token. */
    private record Call(String operation, String method, String path, String body, boolean mutating) {
    }

    private static Call read(String operation, String path) {
        return new Call(operation, "GET", path, null, false);
    }

    private static Call change(String operation, String method, String path, String body) {
        return new Call(operation, method, path, body, true);
    }

    /** Every cookie-protected operation of {@code api/openapi.yaml}. */
    private static List<Call> protectedCalls(long addressId, long orderId) {
        return List.of(
                read("getCurrentCustomer", "/api/v1/auth/me"),
                change("logout", "POST", "/api/v1/auth/logout", null),
                read("listAddresses", "/api/v1/addresses"),
                change("createAddress", "POST", "/api/v1/addresses", ADDRESS),
                change("updateAddress", "PUT", "/api/v1/addresses/" + addressId, ADDRESS),
                change("deleteAddress", "DELETE", "/api/v1/addresses/" + addressId, null),
                read("getBasket", "/api/v1/basket"),
                read("getBasketRecommendations", "/api/v1/recommendations"),
                change("addBasketItem", "POST", "/api/v1/basket/items", "{\"bookId\":1,\"quantity\":1}"),
                change("updateBasketItem", "PUT", "/api/v1/basket/items/1", "{\"quantity\":9}"),
                change("removeBasketItem", "DELETE", "/api/v1/basket/items/1", null),
                change("mergeGuestBasket", "POST", "/api/v1/basket/merge",
                        "{\"items\":[{\"bookId\":1,\"quantity\":2}]}"),
                read("getRewardAccount", "/api/v1/rewards/me"),
                change("quoteCheckout", "POST", "/api/v1/checkout/quote", "{\"pointsToRedeem\":0}"),
                change("placeOrder", "POST", "/api/v1/orders", placeBody("\"addressId\":" + addressId + ",", 0)),
                read("listOrders", "/api/v1/orders"),
                read("getOrder", "/api/v1/orders/" + orderId),
                change("cancelOrder", "POST", "/api/v1/orders/" + orderId + "/cancellation", null),
                change("buyAgain", "POST", "/api/v1/orders/" + orderId + "/buy-again", null));
    }

    private static String placeBody(String addressPart, int points) {
        return "{" + addressPart + "\"paymentMethod\":\"COD\",\"pointsToRedeem\":" + points
                + ",\"expectedPayableTotal\":\"563.95\"}";
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private Cookie loginAs(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"Asha Verma\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                        + "\",\"confirmPassword\":\"" + PASSWORD + "\"}")).andReturn();
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn()
                .getResponse().getCookie("BG_AUTH");
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, Cookie auth) {
        return request.cookie(auth).cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    private static MockHttpServletRequestBuilder build(Call call) {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(call.method()), call.path());
        if (call.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(call.body());
        }
        return builder.header("Idempotency-Key", UUID.randomUUID().toString());
    }

    private long createAddress(Cookie auth) throws Exception {
        MvcResult created = mockMvc.perform(authed(post("/api/v1/addresses"), auth)
                .contentType(MediaType.APPLICATION_JSON).content(ADDRESS)).andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        return JSON.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private void add(Cookie auth, long bookId, int quantity) throws Exception {
        mockMvc.perform(authed(post("/api/v1/basket/items"), auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":" + bookId + ",\"quantity\":" + quantity + "}")).andReturn();
    }

    /** Cash on delivery with a saved address; the basket must already hold the books. */
    private MvcResult placeCod(Cookie auth, long addressId) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders"), auth)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(placeBody("\"addressId\":" + addressId + ",", 0))).andReturn();
    }

    private MvcResult placeWithPayment(Cookie auth, String payment) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/orders"), auth)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":" + ADDRESS + ",\"paymentMethod\":\"CREDIT_CARD\",\"payment\":" + payment
                        + ",\"pointsToRedeem\":0,\"expectedPayableTotal\":\"563.95\"}")).andReturn();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    /** A fingerprint of everything the sweeps must leave untouched. */
    private List<Object> snapshot() {
        List<Object> state = new ArrayList<>();
        state.add(jdbc.queryForList("SELECT id || ':' || customer_id || ':' || city FROM address ORDER BY id"));
        state.add(jdbc.queryForList("SELECT customer_id || ':' || book_id || ':' || quantity FROM basket_item "
                + "ORDER BY customer_id, book_id"));
        state.add(jdbc.queryForList("SELECT id || ':' || customer_id || ':' || status FROM orders ORDER BY id"));
        state.add(jdbc.queryForList("SELECT customer_id || ':' || balance FROM reward_account ORDER BY customer_id"));
        state.add(jdbc.queryForList("SELECT id || ':' || stock_quantity FROM book WHERE stock_quantity IS NOT NULL "
                + "ORDER BY id"));
        state.add(count("reward_transaction"));
        state.add(count("customer"));
        return state;
    }

    private static void assertApiError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(status);
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.hasNonNull("message")).isTrue();
        assertThat(body.hasNonNull("timestamp")).isTrue();
    }

    // ---- authorisation: customers are isolated ------------------------------------------------------------------------

    @Test
    void aCustomerCanNeverReadOrChangeAnotherCustomersData() throws Exception {
        Cookie owner = loginAs("owner@example.com");
        long ownerAddress = createAddress(owner);
        add(owner, 1, 1);
        MvcResult placed = placeCod(owner, ownerAddress);
        assertThat(placed.getResponse().getStatus()).as(placed.getResponse().getContentAsString()).isEqualTo(201);
        long ownerOrder = JSON.readTree(placed.getResponse().getContentAsString()).get("id").asLong();
        add(owner, 13, 1);
        Cookie intruder = loginAs("intruder@example.com");
        add(intruder, 13, 3);
        List<Object> before = snapshot();

        MvcResult updateAddress = mockMvc.perform(authed(request(HttpMethod.PUT, "/api/v1/addresses/" + ownerAddress),
                intruder).contentType(MediaType.APPLICATION_JSON).content(ADDRESS.replace("Bengaluru", "Hacked")))
                .andReturn();
        MvcResult deleteAddress = mockMvc.perform(authed(
                request(HttpMethod.DELETE, "/api/v1/addresses/" + ownerAddress), intruder)).andReturn();
        MvcResult updateLine = mockMvc.perform(authed(request(HttpMethod.PUT, "/api/v1/basket/items/1"), intruder)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}")).andReturn();
        MvcResult removeLine = mockMvc.perform(authed(request(HttpMethod.DELETE, "/api/v1/basket/items/1"),
                intruder)).andReturn();
        MvcResult getOrder = mockMvc.perform(get("/api/v1/orders/" + ownerOrder).cookie(intruder)).andReturn();
        MvcResult cancel = mockMvc.perform(authed(post("/api/v1/orders/" + ownerOrder + "/cancellation"), intruder))
                .andReturn();
        MvcResult buyAgain = mockMvc.perform(authed(post("/api/v1/orders/" + ownerOrder + "/buy-again"), intruder))
                .andReturn();

        for (MvcResult result : List.of(updateAddress, deleteAddress, updateLine, removeLine, getOrder, cancel,
                buyAgain)) {
            assertApiError(result, 404, "NOT_FOUND");
        }
        assertThat(JSON.readTree(mockMvc.perform(get("/api/v1/addresses").cookie(intruder)).andReturn()
                .getResponse().getContentAsString()).size()).isZero();
        assertThat(JSON.readTree(mockMvc.perform(get("/api/v1/orders").cookie(intruder)).andReturn()
                .getResponse().getContentAsString()).size()).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void anotherCustomersSavedAddressCannotBeUsedToPlaceAnOrder() throws Exception {
        Cookie owner = loginAs("owner@example.com");
        long ownerAddress = createAddress(owner);
        Cookie intruder = loginAs("intruder@example.com");
        add(intruder, 1, 1);
        List<Object> before = snapshot();

        MvcResult result = placeCod(intruder, ownerAddress);

        assertApiError(result, 400, "VALIDATION_FAILED");
        assertThat(count("orders")).isZero();
        assertThat(count("payment")).isZero();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void theRewardAccountIsOnlyEverTheCallersOwn() throws Exception {
        Cookie owner = loginAs("owner@example.com");
        Cookie other = loginAs("other@example.com");
        long ownerAddress = createAddress(owner);
        add(owner, 1, 1);
        MvcResult placed = mockMvc.perform(authed(post("/api/v1/orders"), owner)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(placeBody("\"addressId\":" + ownerAddress + ",", 100).replace("563.95", "463.95")))
                .andReturn();
        assertThat(placed.getResponse().getStatus()).as(placed.getResponse().getContentAsString()).isEqualTo(201);

        JsonNode ownerPoints = JSON.readTree(mockMvc.perform(get("/api/v1/rewards/me").cookie(owner)).andReturn()
                .getResponse().getContentAsString());
        JsonNode otherPoints = JSON.readTree(mockMvc.perform(get("/api/v1/rewards/me").cookie(other)).andReturn()
                .getResponse().getContentAsString());

        assertThat(ownerPoints.get("balance").asInt()).isEqualTo(900);
        assertThat(otherPoints.get("balance").asInt()).isEqualTo(1000);
        assertThat(otherPoints.toString()).doesNotContain("REDEEM");
    }

    // ---- authentication and CSRF sweeps -----------------------------------------------------------------------------

    /** State for the sweeps: an address, a placed COD order and one basket line, all belonging to the caller. */
    private record Fixture(Cookie auth, long addressId, long orderId) {
    }

    private Fixture fixture() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long addressId = createAddress(auth);
        add(auth, 1, 1);
        MvcResult placed = placeCod(auth, addressId);
        assertThat(placed.getResponse().getStatus()).as(placed.getResponse().getContentAsString()).isEqualTo(201);
        long orderId = JSON.readTree(placed.getResponse().getContentAsString()).get("id").asLong();
        add(auth, 1, 2);
        return new Fixture(auth, addressId, orderId);
    }

    @Test
    void everyProtectedOperationWithoutTheSessionCookieIsUnauthenticated() throws Exception {
        Fixture fixture = fixture();
        List<Object> before = snapshot();
        List<Call> calls = protectedCalls(fixture.addressId(), fixture.orderId());
        assertThat(calls).hasSize(19);

        for (Call call : calls) {
            MockHttpServletRequestBuilder bare = build(call);
            MvcResult withoutCsrf = mockMvc.perform(bare).andReturn();
            assertApiError(withoutCsrf, 401, "UNAUTHENTICATED");
            if (call.mutating()) {
                MvcResult withCsrf = mockMvc.perform(build(call).cookie(new Cookie("XSRF-TOKEN", CSRF))
                        .header("X-XSRF-TOKEN", CSRF)).andReturn();
                assertApiError(withCsrf, 401, "UNAUTHENTICATED");
            }
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void everyStateChangingOperationWithoutAMatchingCsrfTokenIsRejectedAndChangesNothing() throws Exception {
        Fixture fixture = fixture();
        List<Object> before = snapshot();
        List<Call> mutating = protectedCalls(fixture.addressId(), fixture.orderId()).stream()
                .filter(Call::mutating).toList();
        assertThat(mutating).hasSize(12);

        for (Call call : mutating) {
            MvcResult noToken = mockMvc.perform(build(call).cookie(fixture.auth())).andReturn();
            MvcResult headerOnly = mockMvc.perform(build(call).cookie(fixture.auth())
                    .header("X-XSRF-TOKEN", CSRF)).andReturn();
            MvcResult mismatch = mockMvc.perform(build(call).cookie(fixture.auth())
                    .cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", "another-token")).andReturn();
            for (MvcResult result : List.of(noToken, headerOnly, mismatch)) {
                assertApiError(result, 401, "UNAUTHENTICATED");
            }
        }

        assertThat(snapshot()).isEqualTo(before);
        // the rejected logout did not end the session
        assertThat(mockMvc.perform(get("/api/v1/auth/me").cookie(fixture.auth())).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
    }

    @Test
    void theCookieOnlyReadsAreServedWithoutACsrfTokenAndMutatingCallsAreServedWithBoth() throws Exception {
        Fixture fixture = fixture();

        for (Call call : protectedCalls(fixture.addressId(), fixture.orderId())) {
            if (!call.mutating()) {
                assertThat(mockMvc.perform(build(call).cookie(fixture.auth())).andReturn().getResponse().getStatus())
                        .as(call.operation()).isEqualTo(200);
            }
        }
        MvcResult accepted = mockMvc.perform(authed(build(change("addBasketItem", "POST", "/api/v1/basket/items",
                "{\"bookId\":13,\"quantity\":1}")), fixture.auth())).andReturn();
        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
    }

    // ---- no information disclosure ----------------------------------------------------------------------------------

    private static final Set<String> LEAK_MARKERS = Set.of("Exception", "\tat ", "at com.", "org.springframework",
            "java.lang", "jakarta.", "jdbc", "postgres", "SELECT ", "INSERT ", "reward_account", "order_item",
            "password_hash", "token_version", "basket_item", "stackTrace", "trace");

    private static void assertNoLeak(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        for (String marker : LEAK_MARKERS) {
            assertThat(body).as("response body of %d must not contain '%s'", result.getResponse().getStatus(), marker)
                    .doesNotContain(marker);
        }
        JsonNode json = JSON.readTree(body);
        json.propertyNames().forEach(name ->
                assertThat(Set.of("status", "code", "message", "fieldErrors", "timestamp")).contains(name));
    }

    @Test
    void errorBodiesNeverExposeInternals() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long addressId = createAddress(auth);
        add(auth, 1, 1);
        long orderId = JSON.readTree(placeCod(auth, addressId).getResponse().getContentAsString()).get("id").asLong();
        // a payment that is already REFUNDED cannot be settled again, which forces an unexpected failure (500)
        jdbc.update("UPDATE payment SET status = 'REFUNDED' WHERE order_id = ?", orderId);

        List<MvcResult> failures = List.of(
                mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth)).andReturn(),
                mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                        .andReturn(),
                mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}")).andReturn(),
                mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"x\"}")).andReturn(),
                mockMvc.perform(get("/api/v1/no-such-path").cookie(auth)).andReturn(),
                mockMvc.perform(get("/api/v1/books/abc")).andReturn(),
                mockMvc.perform(get("/api/v1/books/99999999")).andReturn(),
                mockMvc.perform(get("/api/v1/books?page=-1")).andReturn(),
                mockMvc.perform(authed(request(HttpMethod.PATCH, "/api/v1/basket"), auth)).andReturn(),
                mockMvc.perform(get("/api/v1/orders/1").cookie(new Cookie("BG_AUTH", "not.a.jwt"))).andReturn());

        assertThat(failures.get(0).getResponse().getStatus()).isEqualTo(500);
        for (MvcResult failure : failures) {
            assertThat(failure.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
            assertNoLeak(failure);
        }
        assertApiError(failures.get(0), 500, "INTERNAL_ERROR");
        assertApiError(failures.get(9), 401, "UNAUTHENTICATED");
    }

    @Test
    void sqlInjectionTextInASearchIsTreatedAsPlainText() throws Exception {
        long books = count("book");
        String hostile = "x'; DROP TABLE book; --";

        MvcResult result = mockMvc.perform(get("/api/v1/books").param("q", hostile).param("author", hostile)
                .param("brand", "' OR '1'='1")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JSON.readTree(result.getResponse().getContentAsString()).get("items").size()).isZero();
        assertThat(count("book")).isEqualTo(books);
    }

    // ---- no secrets in logs -----------------------------------------------------------------------------------------

    @Test
    void theLogsNeverContainPasswordsCardDataTokensOrTheSigningSecret(CapturedOutput output) throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha.verma@example.com\",\"password\":\"" + WRONG_PASSWORD + "\"}")).andReturn();
        add(auth, 1, 1);
        assertThat(placeWithPayment(auth, DECLINED_CARD).getResponse().getStatus()).isEqualTo(409);
        MvcResult paid = placeWithPayment(auth, CARD);
        assertThat(paid.getResponse().getStatus()).as(paid.getResponse().getContentAsString()).isEqualTo(201);
        long orderId = JSON.readTree(paid.getResponse().getContentAsString()).get("id").asLong();
        jdbc.update("UPDATE payment SET status = 'REFUNDED' WHERE order_id = ?", orderId);
        MvcResult failed = mockMvc.perform(authed(post("/api/v1/orders/" + orderId + "/cancellation"), auth))
                .andReturn();
        assertThat(failed.getResponse().getStatus()).isEqualTo(500);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andReturn();

        String logged = output.getAll();

        // the capture works: the unreadable request was logged (the forced 500 is an ApiException, which is not logged)
        assertThat(logged).contains("Rejected unreadable request");
        assertThat(logged).doesNotContain(PASSWORD).doesNotContain(WRONG_PASSWORD).doesNotContain(CARD_NUMBER)
                .doesNotContain("4111111111111111").doesNotContain("4000-0000-0000-0002")
                .doesNotContain(auth.getValue()).doesNotContain(JWT_SECRET);
        assertThat(logged.toLowerCase()).doesNotContain("cvv").doesNotContain("password_hash")
                .doesNotContain("{pbkdf2}");
    }

    // ---- stored credentials -----------------------------------------------------------------------------------------

    @Test
    void passwordsAreStoredOnlyAsSaltedHashes() throws Exception {
        loginAs("asha.verma@example.com");
        loginAs("ravi.nair@example.com");

        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM customer ORDER BY id", String.class);

        assertThat(hashes).hasSize(2).allSatisfy(hash -> assertThat(hash).startsWith("{pbkdf2}")
                .doesNotContain(PASSWORD));
        assertThat(hashes.get(0)).isNotEqualTo(hashes.get(1));
    }
}
