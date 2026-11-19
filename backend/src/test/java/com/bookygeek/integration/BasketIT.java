package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * The basket against the real database (T085): the {@code basket_item} key and CHECK, upsert on add, isolation
 * between customers, the public preview that writes nothing, the merge report and the 401 / 404 / 409 / 400 paths.
 * Book 1 is a paperback with stock 25, book 13 an eBook, book 5 a paperback with no stock.
 */
class BasketIT extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";

    private Cookie loginAs(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"Asha Verma\",\"email\":\"" + email
                        + "\",\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}")).andReturn();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
        return login.getResponse().getCookie("BG_AUTH");
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, Cookie auth) {
        return request.cookie(auth).cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    private MvcResult add(Cookie auth, long bookId, Integer quantity) throws Exception {
        String body = quantity == null ? "{\"bookId\":" + bookId + "}"
                : "{\"bookId\":" + bookId + ",\"quantity\":" + quantity + "}";
        return mockMvc.perform(authed(post("/api/v1/basket/items"), auth)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM basket_item", Integer.class);
    }

    private int quantityInDb(long bookId) {
        return jdbc.queryForObject("SELECT quantity FROM basket_item WHERE book_id = ?", Integer.class, bookId);
    }

    // ---- database rules -------------------------------------------------------------------------------------------

    @Test
    void addingTheSameBookTwiceKeepsOneRowPerCustomerAndBook() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 2);
        MvcResult second = add(auth, 1, 3);

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(rows()).isEqualTo(1);
        assertThat(quantityInDb(1)).isEqualTo(5);
    }

    @Test
    void theDatabaseRejectsAQuantityOutsideOneToTen() throws Exception {
        loginAs("asha.verma@example.com");
        Long customerId = jdbc.queryForObject("SELECT id FROM customer", Long.class);

        for (int quantity : new int[] {0, 11, -1}) {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO basket_item (customer_id, book_id, quantity, updated_at) VALUES (?, 1, ?, now())",
                    customerId, quantity)).isInstanceOf(DataIntegrityViolationException.class);
        }
        jdbc.update("INSERT INTO basket_item (customer_id, book_id, quantity, updated_at) VALUES (?, 1, 10, now())",
                customerId);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO basket_item (customer_id, book_id, quantity, updated_at) VALUES (?, 1, 1, now())",
                customerId)).as("primary key (customer_id, book_id)").isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- limits ---------------------------------------------------------------------------------------------------

    @Test
    void anEleventhUnitIsAConflictAndTheStoredQuantityIsUnchanged() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        add(auth, 1, 10);
        MvcResult result = add(auth, 1, 1);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(result.getResponse().getContentAsString()).contains("QUANTITY_LIMIT");
        assertThat(quantityInDb(1)).isEqualTo(10);
    }

    @Test
    void aPhysicalBookCannotExceedItsStockButAnEBookCan() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        jdbc.update("UPDATE book SET stock_quantity = 2 WHERE id = 1");

        assertThat(add(auth, 1, 2).getResponse().getStatus()).isEqualTo(200);
        MvcResult over = add(auth, 1, 1);
        assertThat(over.getResponse().getStatus()).isEqualTo(409);
        assertThat(over.getResponse().getContentAsString()).contains("OUT_OF_STOCK");
        assertThat(add(auth, 5, 1).getResponse().getContentAsString()).contains("OUT_OF_STOCK");
        assertThat(add(auth, 13, 10).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void addingDoesNotReserveStock() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        Integer before = jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = 1", Integer.class);
        add(auth, 1, 5);

        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM book WHERE id = 1", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void anUnknownBookIsNotFoundAndInvalidBodiesAreRejected() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");

        assertThat(add(auth, 9999, 1).getResponse().getStatus()).isEqualTo(404);
        assertThat(add(auth, 1, 0).getResponse().getStatus()).isEqualTo(400);
        assertThat(add(auth, 1, 11).getResponse().getStatus()).isEqualTo(400);
        assertThat(add(auth, 0, 1).getResponse().getStatus()).isEqualTo(400);
        assertThat(rows()).isZero();
    }

    // ---- update and remove ----------------------------------------------------------------------------------------

    @Test
    void updateAndRemoveChangeOnlyTheCallersOwnLine() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        add(asha, 1, 2);
        add(ravi, 1, 4);

        MvcResult updated = mockMvc.perform(authed(put("/api/v1/basket/items/1"), asha)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":6}")).andReturn();
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT quantity FROM basket_item ORDER BY quantity", Integer.class))
                .containsExactly(4, 6);

        MvcResult removed = mockMvc.perform(authed(delete("/api/v1/basket/items/1"), asha)).andReturn();
        assertThat(removed.getResponse().getStatus()).isEqualTo(200);
        assertThat(removed.getResponse().getContentAsString()).contains("\"itemCount\":0");
        assertThat(jdbc.queryForList("SELECT quantity FROM basket_item", Integer.class)).containsExactly(4);
    }

    @Test
    void aCustomerCannotChangeOrRemoveALineTheyDoNotHave() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        add(asha, 1, 2);

        MvcResult update = mockMvc.perform(authed(put("/api/v1/basket/items/1"), ravi)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":9}")).andReturn();
        MvcResult remove = mockMvc.perform(authed(delete("/api/v1/basket/items/1"), ravi)).andReturn();

        assertThat(update.getResponse().getStatus()).isEqualTo(404);
        assertThat(remove.getResponse().getStatus()).isEqualTo(404);
        assertThat(quantityInDb(1)).isEqualTo(2);
    }

    @Test
    void eachCustomerSeesOnlyTheirOwnBasket() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        add(asha, 1, 2);
        add(ravi, 13, 1);

        MvcResult ashaBasket = mockMvc.perform(get("/api/v1/basket").cookie(asha)).andReturn();
        MvcResult raviBasket = mockMvc.perform(get("/api/v1/basket").cookie(ravi)).andReturn();

        assertThat(ashaBasket.getResponse().getContentAsString()).contains("\"itemCount\":2")
                .contains("\"deliveryCharge\":\"40.00\"");
        assertThat(raviBasket.getResponse().getContentAsString()).contains("\"itemCount\":1")
                .contains("\"deliveryCharge\":\"0.00\"");
    }

    // ---- authentication -------------------------------------------------------------------------------------------

    @Test
    void everyOperationExceptPreviewNeedsALogin() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/basket")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/v1/basket/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":1}")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(put("/api/v1/basket/items/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(delete("/api/v1/basket/items/1")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/v1/basket/merge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"bookId\":1,\"quantity\":1}]}")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(rows()).isZero();
    }

    @Test
    void aStateChangingCallWithoutTheCsrfHeaderIsRejected() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        MvcResult result = mockMvc.perform(post("/api/v1/basket/items").cookie(auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":1}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(rows()).isZero();
    }

    // ---- preview --------------------------------------------------------------------------------------------------

    @Test
    void previewIsPublicPricesTheLinesAndWritesNothing() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/basket/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"bookId\":1,\"quantity\":2},{\"bookId\":13,\"quantity\":1}]}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"itemCount\":3")
                .contains("\"subtotal\":\"1097.00\"").contains("\"tax\":\"54.85\"")
                .contains("\"deliveryCharge\":\"40.00\"").contains("\"total\":\"1191.85\"");
        assertThat(rows()).isZero();
    }

    @Test
    void previewRejectsAnEmptyOrOversizedRequest() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/basket/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[]}")).andReturn().getResponse().getStatus()).isEqualTo(400);
        StringBuilder items = new StringBuilder();
        for (int i = 1; i <= 101; i++) {
            items.append(i > 1 ? "," : "").append("{\"bookId\":").append(i).append(",\"quantity\":1}");
        }
        assertThat(mockMvc.perform(post("/api/v1/basket/preview").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[" + items + "]}")).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    // ---- merge ----------------------------------------------------------------------------------------------------

    @Test
    void mergeSumsCapsSkipsAndReportsEveryAdjustment() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        jdbc.update("UPDATE book SET stock_quantity = 3 WHERE id = 1");
        add(auth, 13, 2);

        MvcResult result = mockMvc.perform(authed(post("/api/v1/basket/merge"), auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":["
                        + "{\"bookId\":1,\"quantity\":5},{\"bookId\":13,\"quantity\":3},{\"bookId\":5,\"quantity\":1}]}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"reason\":\"QUANTITY_REDUCED\"").contains("\"reason\":\"UNAVAILABLE\"")
                .contains("\"requestedQuantity\":5").contains("\"addedQuantity\":3").contains("\"addedQuantity\":0");
        assertThat(quantityInDb(1)).isEqualTo(3);
        assertThat(quantityInDb(13)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM basket_item WHERE book_id = 5", Integer.class)).isZero();
    }

    @Test
    void mergeWithNothingToAdjustHasAnEmptyReport() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        MvcResult result = mockMvc.perform(authed(post("/api/v1/basket/merge"), auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"bookId\":1,\"quantity\":2}]}")).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"adjustments\":[]");
    }
}
