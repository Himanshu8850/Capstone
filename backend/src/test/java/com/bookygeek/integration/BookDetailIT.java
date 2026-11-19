package com.bookygeek.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code getBook} and {@code listRelatedBooks} against PostgreSQL and the seeded catalogue (T077): an unknown or
 * unlisted id is {@code BOOK_NOT_FOUND}, the detail carries the breadcrumb and the delivery rule, and related books
 * share a category with the book and never include the book itself or an unlisted book. Both endpoints are public.
 */
class BookDetailIT extends IntegrationTestBase {

    @Autowired
    private Clock clock;

    @AfterEach
    void relistEveryBook() {
        jdbc.update("UPDATE book SET listed = TRUE WHERE listed = FALSE");
    }

    private ResultActions book(String id) throws Exception {
        return mockMvc.perform(get("/api/v1/books/" + id));
    }

    private ResultActions related(String id) throws Exception {
        return mockMvc.perform(get("/api/v1/books/" + id + "/related"));
    }

    private List<Integer> relatedIds(long id) throws Exception {
        String json = related(Long.toString(id)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(json, "$[*].id");
    }

    // ---- getBook ---------------------------------------------------------------------------------------------------

    @Test
    void aGuestCanReadAPhysicalBookWithItsDetails() throws Exception {
        LocalDate today = LocalDate.now(clock);

        book("1").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("The Habit Ladder"))
                .andExpect(jsonPath("$.author").value("Meera Iyer"))
                .andExpect(jsonPath("$.publisher").value("Maple Leaf Press"))
                .andExpect(jsonPath("$.format").value("PAPERBACK"))
                .andExpect(jsonPath("$.language").value("English"))
                .andExpect(jsonPath("$.price").value("499.00"))
                .andExpect(jsonPath("$.frontCoverUrl").value("/covers/1-front.svg"))
                .andExpect(jsonPath("$.backCoverUrl").value("/covers/1-back.svg"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.stockQuantity").value(25))
                .andExpect(jsonPath("$.deliveryDate").value(today.plusDays(5).toString()))
                .andExpect(jsonPath("$.categories[0].name").value("Self Help"));
    }

    @Test
    void anEbookHasNoStockCountIsAlwaysAvailableAndIsDeliveredToday() throws Exception {
        LocalDate today = LocalDate.now(clock);

        book("13").andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("EBOOK"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.stockQuantity").doesNotExist())
                .andExpect(jsonPath("$.deliveryDate").value(today.toString()));
        // The field is present as an explicit null, as the contract requires.
        assertThat(book("13").andReturn().getResponse().getContentAsString()).contains("\"stockQuantity\":null");
    }

    @Test
    void aPhysicalBookWithNoStockIsUnavailableButKeepsTheFiveDayDeliveryDate() throws Exception {
        LocalDate today = LocalDate.now(clock);

        book("5").andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.stockQuantity").value(0))
                .andExpect(jsonPath("$.deliveryDate").value(today.plusDays(5).toString()));
    }

    @Test
    void theBreadcrumbFollowsTheCategoryHierarchy() throws Exception {
        book("7").andExpect(jsonPath("$.breadcrumb.length()").value(2))
                .andExpect(jsonPath("$.breadcrumb[0].id").value(2))
                .andExpect(jsonPath("$.breadcrumb[0].name").value("Non-Fiction"))
                .andExpect(jsonPath("$.breadcrumb[1].id").value(14))
                .andExpect(jsonPath("$.breadcrumb[1].name").value("Self Help"));
        // Book 23 is Science Fiction, under Fiction.
        book("23").andExpect(jsonPath("$.breadcrumb[0].name").value("Fiction"))
                .andExpect(jsonPath("$.breadcrumb[1].name").value("Science Fiction"));
    }

    @Test
    void aBookInTwoCategoriesListsBothAndUsesTheLowerCategoryIdForTheBreadcrumb() throws Exception {
        // Book 54 is Philosophy (19) and Religion (20), both under Non-Fiction.
        book("54").andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.breadcrumb[1].id").value(19))
                .andExpect(jsonPath("$.breadcrumb[1].name").value("Philosophy"));
    }

    @Test
    void anUnknownBookIsBookNotFound() throws Exception {
        book("9999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
        related("9999").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void anUnlistedBookIsBookNotFoundOnBothEndpoints() throws Exception {
        jdbc.update("UPDATE book SET listed = FALSE WHERE id = 2");

        book("2").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
        related("2").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void anIdThatIsNotANumberOrIsOutOfRangeIsNotFoundWithTheStandardErrorBody() throws Exception {
        book("abc").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        book("-1").andExpect(status().isNotFound());
        book("0").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
        book("99999999999999999999").andExpect(status().isNotFound());
    }

    @Test
    void theErrorBodyNeverRevealsInternals() throws Exception {
        String body = book("9999").andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("Exception").doesNotContain("SELECT").doesNotContain("com.bookygeek");
    }

    // ---- listRelatedBooks ------------------------------------------------------------------------------------------

    @Test
    void relatedBooksShareACategoryWithTheBookAndExcludeTheBookItself() throws Exception {
        List<Integer> ids = relatedIds(7);

        assertThat(ids).isNotEmpty().doesNotContain(7);
        List<Integer> sharing = jdbc.queryForList("SELECT DISTINCT bc.book_id FROM book_category bc WHERE bc.category_id "
                + "IN (SELECT category_id FROM book_category WHERE book_id = 7) AND bc.book_id <> 7", Integer.class);
        assertThat(ids).containsExactlyInAnyOrderElementsOf(sharing);
    }

    @Test
    void aBookInTwoCategoriesGetsTheBooksOfBothWithoutDuplicates() throws Exception {
        // Book 54 is in 19 (52, 53) and 20 (55).
        List<Integer> ids = relatedIds(54);

        assertThat(ids).containsExactlyInAnyOrder(52, 53, 55);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void booksInAnotherCategoryAreNotRelated() throws Exception {
        // Self Help (14) books are not related to Cooking (17) books.
        assertThat(relatedIds(46)).containsExactlyInAnyOrder(47, 48).doesNotContain(1, 2, 3);
    }

    @Test
    void relatedBooksNeverIncludeAnUnlistedBook() throws Exception {
        jdbc.update("UPDATE book SET listed = FALSE WHERE id = 53");

        assertThat(relatedIds(54)).containsExactlyInAnyOrder(52, 55);
    }

    @Test
    void aBookWithNoOtherBookInItsCategoriesHasAnEmptyList() throws Exception {
        // Book 39 is the only Drama book.
        related("39").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void relatedBooksAreFullSummariesWithTheDeliveryRule() throws Exception {
        LocalDate today = LocalDate.now(clock);

        // Self Help holds both physical books and the eBook 13, so both delivery rules are checked.
        String json = related("1").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> formats = JsonPath.read(json, "$[*].format");
        List<String> dates = JsonPath.read(json, "$[*].deliveryDate");
        for (int i = 0; i < formats.size(); i++) {
            String expected = "EBOOK".equals(formats.get(i)) ? today.toString() : today.plusDays(5).toString();
            assertThat(dates.get(i)).isEqualTo(expected);
        }
        assertThat(formats).contains("EBOOK");
        assertThat(json).contains("\"categories\":[{").contains("\"frontCoverUrl\":\"/covers/");
    }

    @Test
    void relatedBooksAreInAvailableFirstThenNewestOrder() throws Exception {
        // Book 5 is the only physical Self Help book with no stock, so it must come last.
        List<Integer> ids = relatedIds(1);

        assertThat(ids).contains(5);
        assertThat(ids.get(ids.size() - 1)).isEqualTo(5);
        assertThat(ids.get(0)).isEqualTo(14);
    }
}
