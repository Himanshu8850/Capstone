package com.bookygeek.contract;

import com.bookygeek.integration.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Validates the real responses of {@code getRewardAccount} against {@code api/openapi.yaml} (T127): the closed
 * {@code RewardAccount} schema for 200 and the {@code ApiError} shape for 401.
 */
class RewardContractTest extends IntegrationTestBase {

    private Cookie login() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"Asha Verma\",\"email\":\"asha.verma@example.com\","
                        + "\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}")).andReturn();
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha.verma@example.com\",\"password\":\"" + PASSWORD + "\"}")).andReturn()
                .getResponse().getCookie("BG_AUTH");
    }

    private static void check(int expectedStatus, MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString())
                .isEqualTo(expectedStatus);
        ContractSupport.assertResponse("getRewardAccount", result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    @Test
    void getRewardAccountMatchesTheContractForEveryDeclaredStatus() throws Exception {
        Cookie auth = login();

        MvcResult account = mockMvc.perform(get("/api/v1/rewards/me").cookie(auth)).andReturn();
        check(200, account);
        assertThat(account.getResponse().getContentAsString()).isEqualTo("{\"balance\":1000}");

        check(401, mockMvc.perform(get("/api/v1/rewards/me")).andReturn());
    }

    @Test
    void getRewardAccountMatchesTheContractForAReducedAndAZeroBalance() throws Exception {
        Cookie auth = login();

        jdbc.update("UPDATE reward_account SET balance = 719");
        check(200, mockMvc.perform(get("/api/v1/rewards/me").cookie(auth)).andReturn());

        jdbc.update("UPDATE reward_account SET balance = 0");
        MvcResult zero = mockMvc.perform(get("/api/v1/rewards/me").cookie(auth)).andReturn();
        check(200, zero);
        assertThat(zero.getResponse().getContentAsString()).isEqualTo("{\"balance\":0}");
    }
}
