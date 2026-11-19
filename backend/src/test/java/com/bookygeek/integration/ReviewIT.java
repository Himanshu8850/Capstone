package com.bookygeek.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Book reviews against the real database (AMD-002, T158): the public list and its average, creating a review, one
 * review per customer per book, validation of rating and comment, sign-in and unknown books.
 */
class ReviewIT extends IntegrationTestBase {

    private static final String CSRF = "csrf-token-for-tests";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Cookie loginAs(String name, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"" + name + "\",\"email\":\"" + email
                        + "\",\"password\":\"" + PASSWORD + "\",\"confirmPassword\":\"" + PASSWORD + "\"}")).andReturn();
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn()
                .getResponse().getCookie("BG_AUTH");
    }

    private static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", CSRF)).header("X-XSRF-TOKEN", CSRF);
    }

    private MvcResult review(Cookie auth, long bookId, String body) throws Exception {
        MockHttpServletRequestBuilder request = withCsrf(post("/api/v1/books/" + bookId + "/reviews"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mockMvc.perform(auth == null ? request : request.cookie(auth)).andReturn();
    }

    private JsonNode list(long bookId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/books/" + bookId + "/reviews")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void aBookWithoutReviewsHasANullAverageAndAnEmptyList() throws Exception {
        JsonNode body = list(1);

        assertThat(body.get("averageRating").isNull()).isTrue();
        assertThat(body.get("reviewCount").asInt()).isZero();
        assertThat(body.get("reviews")).isEmpty();
    }

    @Test
    void aCreatedReviewIsReturnedWith201AndListedWithTheReviewersName() throws Exception {
        Cookie auth = loginAs("Asha Verma", "asha.verma@example.com");

        MvcResult created = review(auth, 1, "{\"rating\":5,\"comment\":\"Great read\"}");

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode one = JSON.readTree(created.getResponse().getContentAsString());
        assertThat(one.get("reviewerName").asString()).isEqualTo("Asha Verma");
        assertThat(one.get("rating").asInt()).isEqualTo(5);
        assertThat(one.get("comment").asString()).isEqualTo("Great read");
        assertThat(one.get("id").asLong()).isPositive();
        assertThat(one.get("createdAt").asString()).isNotBlank();

        JsonNode body = list(1);
        assertThat(body.get("reviewCount").asInt()).isEqualTo(1);
        assertThat(body.get("averageRating").asDouble()).isEqualTo(5.0);
        assertThat(body.get("reviews").get(0).get("reviewerName").asString()).isEqualTo("Asha Verma");
        assertThat(body.get("reviews").get(0).get("comment").asString()).isEqualTo("Great read");
    }

    @Test
    void theCommentIsOptionalAndIsNullInTheList() throws Exception {
        Cookie auth = loginAs("Asha Verma", "asha.verma@example.com");

        assertThat(review(auth, 1, "{\"rating\":3}").getResponse().getStatus()).isEqualTo(201);

        assertThat(list(1).get("reviews").get(0).get("comment").isNull()).isTrue();
    }

    @Test
    void theAverageIsRoundedHalfUpToOneDecimalAndTheNewestReviewComesFirst() throws Exception {
        int[] ratings = {5, 4, 4, 4};
        for (int i = 0; i < ratings.length; i++) {
            Cookie auth = loginAs("Reader " + i, "reader" + i + "@example.com");
            assertThat(review(auth, 1, "{\"rating\":" + ratings[i] + "}").getResponse().getStatus()).isEqualTo(201);
        }

        JsonNode body = list(1);

        // 17 / 4 = 4.25, which HALF_UP makes 4.3 (HALF_EVEN would give 4.2).
        assertThat(body.get("averageRating").decimalValue()).isEqualByComparingTo("4.3");
        assertThat(body.get("reviewCount").asInt()).isEqualTo(4);
        assertThat(body.get("reviews").get(0).get("reviewerName").asString()).isEqualTo("Reader 3");
        assertThat(body.get("reviews").get(3).get("reviewerName").asString()).isEqualTo("Reader 0");
    }

    @Test
    void aSecondReviewOfTheSameBookIsRefusedWithAlreadyReviewed() throws Exception {
        Cookie auth = loginAs("Asha Verma", "asha.verma@example.com");
        review(auth, 1, "{\"rating\":5}");

        MvcResult second = review(auth, 1, "{\"rating\":1}");

        assertThat(second.getResponse().getStatus()).isEqualTo(409);
        JsonNode error = JSON.readTree(second.getResponse().getContentAsString());
        assertThat(error.get("code").asString()).isEqualTo("ALREADY_REVIEWED");
        assertThat(error.get("message").asString()).isEqualTo("You have already reviewed this book.");
        assertThat(list(1).get("reviewCount").asInt()).isEqualTo(1);
        // The same customer may still review a different book.
        assertThat(review(auth, 13, "{\"rating\":2}").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void aRatingOutsideOneToFiveOrMissingAndALongCommentAreRejectedWithFieldErrors() throws Exception {
        Cookie auth = loginAs("Asha Verma", "asha.verma@example.com");

        for (String body : new String[] {"{\"rating\":0}", "{\"rating\":6}", "{\"comment\":\"No rating\"}"}) {
            MvcResult result = review(auth, 1, body);
            assertThat(result.getResponse().getStatus()).as(body).isEqualTo(400);
            String json = result.getResponse().getContentAsString();
            assertThat(json).contains("\"code\":\"VALIDATION_FAILED\"").contains("\"field\":\"rating\"")
                    .contains("Select a rating from 1 to 5.");
        }

        MvcResult tooLong = review(auth, 1, "{\"rating\":4,\"comment\":\"" + "x".repeat(101) + "\"}");
        assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
        assertThat(tooLong.getResponse().getContentAsString()).contains("\"field\":\"comment\"")
                .contains("Comment must be 100 characters or fewer.");

        MvcResult exactly100 = review(auth, 1, "{\"rating\":4,\"comment\":\"" + "x".repeat(100) + "\"}");
        assertThat(exactly100.getResponse().getStatus()).isEqualTo(201);
        assertThat(list(1).get("reviewCount").asInt()).isEqualTo(1);
    }

    @Test
    void postingWithoutBeingSignedInIsUnauthenticatedAndStoresNothing() throws Exception {
        MvcResult result = review(null, 1, "{\"rating\":5}");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review", Integer.class)).isZero();
    }

    @Test
    void anUnknownOrUnlistedBookIsBookNotFoundForBothOperations() throws Exception {
        Cookie auth = loginAs("Asha Verma", "asha.verma@example.com");
        jdbc.update("UPDATE book SET listed = FALSE WHERE id = 2");
        try {
            for (long id : new long[] {9999, 2}) {
                MvcResult read = mockMvc.perform(get("/api/v1/books/" + id + "/reviews")).andReturn();
                assertThat(read.getResponse().getStatus()).as("GET " + id).isEqualTo(404);
                assertThat(read.getResponse().getContentAsString()).contains("\"code\":\"BOOK_NOT_FOUND\"");

                MvcResult write = review(auth, id, "{\"rating\":5}");
                assertThat(write.getResponse().getStatus()).as("POST " + id).isEqualTo(404);
                assertThat(write.getResponse().getContentAsString()).contains("\"code\":\"BOOK_NOT_FOUND\"");
            }
        } finally {
            jdbc.update("UPDATE book SET listed = TRUE WHERE id = 2");
        }
    }
}