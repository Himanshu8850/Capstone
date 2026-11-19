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
 * Saved addresses and the states list against the real database (T101): create, list, update, delete, ownership,
 * the India state rule, field validation and the database CHECKs on phone and PIN code.
 */
class AddressIT extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";

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

    private static String address(String city, String state, String pin, String country) {
        return "{\"fullName\":\"Asha Verma\",\"phone\":\"9876543210\",\"addressLine1\":\"12 MG Road\","
                + "\"city\":\"" + city + "\",\"state\":\"" + state + "\",\"pinCode\":\"" + pin + "\""
                + (country == null ? "" : ",\"country\":\"" + country + "\"") + "}";
    }

    private MvcResult create(Cookie auth, String body) throws Exception {
        return mockMvc.perform(authed(post("/api/v1/addresses"), auth).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn();
    }

    private static long idOf(MvcResult created) throws Exception {
        return Long.parseLong(created.getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private int rows() {
        return jdbc.queryForObject("SELECT count(*) FROM address", Integer.class);
    }

    @Test
    void creatingAnAddressStoresItForTheCallerWithIndiaAsTheDefaultCountry() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");

        MvcResult created = create(auth, address("Bengaluru", "Karnataka", "560001", null));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/addresses/" + idOf(created));
        assertThat(created.getResponse().getContentAsString()).contains("\"country\":\"India\"")
                .contains("\"state\":\"Karnataka\"").doesNotContain("addressLine2");
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void theStateIsMatchedCaseInsensitivelyAndStoredInItsListedForm() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");

        MvcResult created = create(auth, address("Bengaluru", "karnataka", "560001", "india"));

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(created.getResponse().getContentAsString()).contains("\"state\":\"Karnataka\"")
                .contains("\"country\":\"India\"");
    }

    @Test
    void anUnknownIndianStateIsRejectedButAnyStateIsAcceptedForAnotherCountry() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");

        MvcResult unknown = create(auth, address("Bengaluru", "Atlantis", "560001", null));
        MvcResult abroad = create(auth, address("Leeds", "West Yorkshire", "560001", "United Kingdom"));

        assertThat(unknown.getResponse().getStatus()).isEqualTo(400);
        assertThat(unknown.getResponse().getContentAsString()).contains("\"field\":\"state\"");
        assertThat(abroad.getResponse().getStatus()).isEqualTo(201);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void invalidPhoneAndPinCodeAreRejected() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");

        MvcResult shortPin = create(auth, address("Pune", "Maharashtra", "4110", null));
        MvcResult badPhone = create(auth, address("Pune", "Maharashtra", "411001", null)
                .replace("9876543210", "98765"));
        MvcResult blank = create(auth, "{}");

        assertThat(shortPin.getResponse().getStatus()).isEqualTo(400);
        assertThat(badPhone.getResponse().getStatus()).isEqualTo(400);
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(rows()).isZero();
    }

    @Test
    void theDatabaseRejectsAPhoneOrPinCodeThatIsNotAllDigits() throws Exception {
        loginAs("asha.verma@example.com");
        Long customerId = jdbc.queryForObject("SELECT id FROM customer", Long.class);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO address (customer_id, full_name, phone, address_line1, city, "
                + "state, pin_code, country, created_at, updated_at) VALUES (?, 'A', '98765abcde', 'x', 'c', 's', "
                + "'560001', 'India', now(), now())", customerId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO address (customer_id, full_name, phone, address_line1, city, "
                + "state, pin_code, country, created_at, updated_at) VALUES (?, 'A', '9876543210', 'x', 'c', 's', "
                + "'56A001', 'India', now(), now())", customerId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void listingReturnsOnlyTheCallersAddressesOldestFirst() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        create(asha, address("Bengaluru", "Karnataka", "560001", null));
        create(asha, address("Pune", "Maharashtra", "411001", null));
        create(ravi, address("Kochi", "Kerala", "682001", null));

        String body = mockMvc.perform(get("/api/v1/addresses").cookie(asha)).andReturn().getResponse()
                .getContentAsString();

        assertThat(body).contains("Bengaluru").contains("Pune").doesNotContain("Kochi");
        assertThat(body.indexOf("Bengaluru")).isLessThan(body.indexOf("Pune"));
    }

    @Test
    void updatingReplacesTheAddressAndOnlyTheOwnerMayDoIt() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        long id = idOf(create(asha, address("Bengaluru", "Karnataka", "560001", null)));

        MvcResult updated = mockMvc.perform(authed(put("/api/v1/addresses/" + id), asha)
                .contentType(MediaType.APPLICATION_JSON).content(address("Mysuru", "Karnataka", "570001", null)))
                .andReturn();
        MvcResult foreign = mockMvc.perform(authed(put("/api/v1/addresses/" + id), ravi)
                .contentType(MediaType.APPLICATION_JSON).content(address("Hacked", "Karnataka", "570001", null)))
                .andReturn();

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(updated.getResponse().getContentAsString()).contains("Mysuru").contains("570001");
        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT city FROM address", String.class)).isEqualTo("Mysuru");
    }

    @Test
    void deletingRemovesTheAddressAndOnlyTheOwnerMayDoIt() throws Exception {
        Cookie asha = loginAs("asha.verma@example.com");
        Cookie ravi = loginAs("ravi.nair@example.com");
        long id = idOf(create(asha, address("Bengaluru", "Karnataka", "560001", null)));

        MvcResult foreign = mockMvc.perform(authed(delete("/api/v1/addresses/" + id), ravi)).andReturn();
        assertThat(foreign.getResponse().getStatus()).isEqualTo(404);
        assertThat(rows()).isEqualTo(1);

        MvcResult removed = mockMvc.perform(authed(delete("/api/v1/addresses/" + id), asha)).andReturn();
        assertThat(removed.getResponse().getStatus()).isEqualTo(204);
        assertThat(rows()).isZero();
        assertThat(mockMvc.perform(authed(delete("/api/v1/addresses/" + id), asha)).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
    }

    @Test
    void deletingASavedAddressDoesNotChangeAnOrderPlacedWithIt() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        long id = idOf(create(auth, address("Bengaluru", "Karnataka", "560001", null)));
        mockMvc.perform(authed(post("/api/v1/basket/items"), auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":1,\"quantity\":1}")).andReturn();
        MvcResult placed = mockMvc.perform(authed(post("/api/v1/orders"), auth)
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + id
                        + ",\"paymentMethod\":\"COD\",\"pointsToRedeem\":0,\"expectedPayableTotal\":\"563.95\"}"))
                .andReturn();
        assertThat(placed.getResponse().getStatus()).isEqualTo(201);

        mockMvc.perform(authed(delete("/api/v1/addresses/" + id), auth)).andReturn();
        String orderId = placed.getResponse().getHeader("Location");
        MvcResult fetched = mockMvc.perform(get(orderId).cookie(auth)).andReturn();

        assertThat(fetched.getResponse().getStatus()).isEqualTo(200);
        assertThat(fetched.getResponse().getContentAsString()).contains("Bengaluru");
    }

    @Test
    void addressOperationsNeedALoginAndTheCsrfHeader() throws Exception {
        Cookie auth = loginAs("asha.verma@example.com");
        String body = address("Bengaluru", "Karnataka", "560001", null);

        assertThat(mockMvc.perform(get("/api/v1/addresses")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/v1/addresses").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/v1/addresses").cookie(auth).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(delete("/api/v1/addresses/1")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(rows()).isZero();
    }

    @Test
    void theStatesListIsPublicSortedAndComplete() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reference/states")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"Karnataka\"").contains("\"Delhi\"").contains("\"Ladakh\"");
        assertThat(body.split("\",\"").length).isEqualTo(36);
        assertThat(body.indexOf("Andhra Pradesh")).isLessThan(body.indexOf("West Bengal"));
    }
}
