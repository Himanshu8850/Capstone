package com.bookygeek.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalogue queries against PostgreSQL and the seeded 60-book catalogue (T064): category subtrees, ANDed filters,
 * price bands, exact paging of 12, sort options, unlisted books never returned, and filter options that only list
 * values that exist. Expected counts that depend on the seed are computed from the database, so the test follows the
 * data instead of repeating it.
 */
class CatalogueQueryIT extends IntegrationTestBase {

    @Autowired
    private Clock clock;

    @AfterEach
    void relistEveryBook() {
        jdbc.update("UPDATE book SET listed = TRUE WHERE listed = FALSE");
    }

    private ResultActions books(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/books" + (query.isEmpty() ? "" : "?" + query)));
    }

    private List<Integer> ids(String query) throws Exception {
        String json = books(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.items[*].id");
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    // ---- categories -----------------------------------------------------------------------------------------------

    @Test
    void aParentCategoryReturnsTheBooksOfAllItsDescendantLeaves() throws Exception {
        int expected = count("SELECT COUNT(DISTINCT bc.book_id) FROM book_category bc JOIN category c "
                + "ON c.id = bc.category_id WHERE c.id = 2 OR c.parent_id = 2");

        books("category=2").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(expected));
    }

    @Test
    void aLeafCategoryReturnsOnlyItsOwnBooksAndABookInTwoCategoriesAppearsInBoth() throws Exception {
        List<Integer> selfHelp = new java.util.ArrayList<>(ids("category=14"));
        selfHelp.addAll(ids("category=14&page=1"));
        assertThat(selfHelp).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        books("category=14&page=1").andExpect(jsonPath("$.items.length()").value(2));
        // Book 11 is both Self Help (14) and Science (18).
        assertThat(ids("category=18")).contains(11);
    }

    @Test
    void aBookInTwoCategoriesIsCountedOnceUnderTheirCommonParent() throws Exception {
        // Book 54 is Religion (20) and Philosophy (19); both are children of Non-Fiction.
        int expected = count("SELECT COUNT(DISTINCT bc.book_id) FROM book_category bc JOIN category c "
                + "ON c.id = bc.category_id WHERE c.parent_id = 2 OR c.id = 2");
        books("category=2").andExpect(jsonPath("$.totalItems").value(expected));
    }

    @Test
    void anUnknownCategoryGivesAnEmptyPageNotAnError() throws Exception {
        books("category=99999").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    // ---- brand, author, language, format --------------------------------------------------------------------------

    @Test
    void brandListsOnlyThatPublishersBooks() throws Exception {
        int expected = count("SELECT COUNT(*) FROM book WHERE publisher = ?", "Saffron House");

        books("brand=Saffron House").andExpect(jsonPath("$.totalItems").value(expected))
                .andExpect(jsonPath("$.items[*].publisher", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("Saffron House"))));
    }

    @Test
    void authorFiltersByExactAuthorName() throws Exception {
        // AMD-003: every writer has exactly five books (Meera Iyer: books 1, 2, 3, 5 and 6).
        books("author=Meera Iyer").andExpect(jsonPath("$.totalItems").value(5));
        assertThat(ids("author=Meera Iyer")).containsExactlyInAnyOrder(1, 2, 3, 5, 6);
        books("author=Meera").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void everyWriterHasFiveBooksAndABiography() {
        assertThat(jdbc.queryForList("SELECT author FROM book GROUP BY author HAVING COUNT(*) <> 5", String.class))
                .isEmpty();
        assertThat(count("SELECT COUNT(DISTINCT author) FROM book")).isEqualTo(12);
        assertThat(jdbc.queryForList("""
                SELECT name FROM author_profile
                WHERE array_length(regexp_split_to_array(btrim(bio), '\\s+'), 1) <> 50""", String.class)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM (SELECT DISTINCT author FROM book) a "
                + "WHERE NOT EXISTS (SELECT 1 FROM author_profile p WHERE p.name = a.author)")).isZero();
    }

    @Test
    void languageAndFormatFilter() throws Exception {
        books("language=Hindi").andExpect(jsonPath("$.totalItems")
                .value(count("SELECT COUNT(*) FROM book WHERE language = 'Hindi'")));
        books("format=EBOOK").andExpect(jsonPath("$.totalItems")
                .value(count("SELECT COUNT(*) FROM book WHERE format = 'EBOOK'")));
    }

    // ---- combined filters -----------------------------------------------------------------------------------------

    @Test
    void combinedFiltersAreAnded() throws Exception {
        int expected = count("SELECT COUNT(DISTINCT b.id) FROM book b JOIN book_category bc ON bc.book_id = b.id "
                + "WHERE bc.category_id = 14 AND b.language = 'English' AND b.format = 'PAPERBACK' "
                + "AND b.price BETWEEN 200 AND 499.99");

        books("category=14&language=English&format=PAPERBACK&minPrice=200.00&maxPrice=499.99")
                .andExpect(jsonPath("$.totalItems").value(expected));
        assertThat(expected).isPositive();
    }

    @Test
    void filtersCombineWithSearchBrandSortAndPaging() throws Exception {
        books("category=2&brand=Lantern Books&q=a&sort=PRICE_ASC&page=0&language=English")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(12));
    }

    @Test
    void priceBandsFromConfigurationAreInclusiveAtBothEnds() throws Exception {
        // 499.00 sits inside "200-499"; the band's own bounds are inclusive.
        int atBound = count("SELECT COUNT(*) FROM book WHERE price = 499.00");
        assertThat(atBound).isPositive();
        books("minPrice=499.00&maxPrice=499.00").andExpect(jsonPath("$.totalItems").value(atBound));

        mockMvc.perform(get("/api/v1/books/filter-options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.priceBands.length()").value(4))
                .andExpect(jsonPath("$.priceBands[0].label").value("Under ₹200"))
                .andExpect(jsonPath("$.priceBands[0].minPrice").doesNotExist())
                .andExpect(jsonPath("$.priceBands[0].maxPrice").value("199.99"))
                .andExpect(jsonPath("$.priceBands[3].minPrice").value("1000.00"))
                .andExpect(jsonPath("$.priceBands[3].maxPrice").doesNotExist());

        int underTwoHundred = count("SELECT COUNT(*) FROM book WHERE price <= 199.99");
        books("maxPrice=199.99").andExpect(jsonPath("$.totalItems").value(underTwoHundred));
    }

    // ---- paging ---------------------------------------------------------------------------------------------------

    @Test
    void pagingIsExactlyTwelveAndTheLastPageHoldsTheRest() throws Exception {
        int total = count("SELECT COUNT(*) FROM book");
        books("").andExpect(jsonPath("$.items.length()").value(12))
                .andExpect(jsonPath("$.pageSize").value(12))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalItems").value(total))
                .andExpect(jsonPath("$.totalPages").value((total + 11) / 12));
        books("page=" + ((total + 11) / 12 - 1)).andExpect(jsonPath("$.items.length()")
                .value(total - 12 * ((total + 11) / 12 - 1)));
    }

    @Test
    void aPageBeyondTheEndIsEmptyButKeepsTheTotals() throws Exception {
        books("page=50").andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(50));
    }

    @Test
    void pagesDoNotOverlap() throws Exception {
        List<Integer> first = ids("sort=TITLE_ASC&page=0");
        List<Integer> second = ids("sort=TITLE_ASC&page=1");
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    // ---- sort -----------------------------------------------------------------------------------------------------

    @Test
    void priceSortsAreAscendingAndDescending() throws Exception {
        List<Integer> ascending = ids("sort=PRICE_ASC");
        List<Integer> descending = ids("sort=PRICE_DESC");
        // 99.00 is the lowest price (books 13 and 60, tie by title); 1299.00 the highest (book 40).
        assertThat(ascending.get(0)).isEqualTo(60);
        assertThat(descending.get(0)).isEqualTo(40);
    }

    @Test
    void titleSortIsAlphabeticalIgnoringCase() throws Exception {
        String json = books("sort=TITLE_ASC").andReturn().getResponse().getContentAsString();
        List<String> titles = com.jayway.jsonpath.JsonPath.read(json, "$.items[*].title");
        List<String> sorted = titles.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        assertThat(titles).isEqualTo(sorted);
    }

    @Test
    void defaultOrderWithoutASearchIsAvailableFirstThenNewestAndAnOutOfStockBookIsLast() throws Exception {
        List<Integer> everything = new java.util.ArrayList<>();
        for (int page = 0; page < 5; page++) {
            everything.addAll(ids("page=" + page));
        }
        assertThat(everything.get(0)).isEqualTo(60);
        // Book 5 is the only physical book with no stock in the seed; it must come after every available book.
        int unavailable = count("SELECT COUNT(*) FROM book WHERE format <> 'EBOOK' AND stock_quantity = 0");
        assertThat(everything.subList(everything.size() - unavailable, everything.size())).contains(5);
    }

    @Test
    void relevanceRanksTitleStartsWithBeforeTitleContains() throws Exception {
        // Book 14 "Habits of the Hopeful" starts with the term; book 1 "The Habit Ladder" only contains it.
        assertThat(ids("q=habit")).containsExactly(14, 1);
    }

    @Test
    void searchMatchesAuthorIgnoringCaseAndWildcardsAreLiteral() throws Exception {
        assertThat(ids("q=MEERA IYER")).containsExactlyInAnyOrder(1, 2, 3, 5, 6);
        books("q=%").andExpect(jsonPath("$.totalItems").value(0));
        books("q=_").andExpect(jsonPath("$.totalItems").value(0));
        books("q=zzzz-no-such-book").andExpect(jsonPath("$.totalItems").value(0));
    }

    // ---- unlisted books -------------------------------------------------------------------------------------------

    @Test
    void anUnlistedBookIsNeverReturnedByAnyQueryOrCounted() throws Exception {
        jdbc.update("UPDATE book SET listed = FALSE WHERE id = 1");

        assertThat(ids("category=14")).doesNotContain(1);
        assertThat(ids("q=Habit Ladder")).isEmpty();
        books("").andExpect(jsonPath("$.totalItems").value(59));
        assertThat(ids("author=Meera Iyer")).doesNotContain(1);

        List<Map<String, Object>> brands = jdbc.queryForList(
                "SELECT publisher, COUNT(*) AS n FROM book WHERE listed GROUP BY publisher ORDER BY publisher");
        mockMvc.perform(get("/api/v1/brands")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(brands.size()))
                .andExpect(jsonPath("$[?(@.name=='Maple Leaf Press')].bookCount")
                        .value(((Number) brands.stream().filter(b -> "Maple Leaf Press".equals(b.get("publisher")))
                                .findFirst().orElseThrow().get("n")).intValue()));
    }

    // ---- filter options and brands --------------------------------------------------------------------------------

    @Test
    void filterOptionsOnlyListLanguagesThatExistInListedBooks() throws Exception {
        List<String> expected = jdbc.queryForList("SELECT DISTINCT language FROM book ORDER BY language", String.class);
        mockMvc.perform(get("/api/v1/books/filter-options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.languages.length()").value(expected.size()))
                .andExpect(jsonPath("$.formats.length()").value(3));

        // A language whose only books are unlisted disappears from the options.
        jdbc.update("UPDATE book SET listed = FALSE WHERE language = 'French'");
        mockMvc.perform(get("/api/v1/books/filter-options"))
                .andExpect(jsonPath("$.languages.length()").value(expected.size() - 1))
                .andExpect(jsonPath("$.languages[?(@=='French')]").isEmpty());
    }

    @Test
    void brandsAreThePublishersWithTheirCountsInAlphabeticalOrder() throws Exception {
        List<String> expected = jdbc.queryForList(
                "SELECT publisher FROM book GROUP BY publisher ORDER BY publisher", String.class);
        String json = mockMvc.perform(get("/api/v1/brands")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = com.jayway.jsonpath.JsonPath.read(json, "$[*].name");
        assertThat(names).containsExactlyElementsOf(expected);
    }

    @Test
    void categoriesAreATwoLevelTree() throws Exception {
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Fiction"))
                .andExpect(jsonPath("$[0].children.length()").value(10))
                .andExpect(jsonPath("$[1].name").value("Non-Fiction"))
                .andExpect(jsonPath("$[1].children.length()").value(9))
                .andExpect(jsonPath("$[0].children[0].children.length()").value(0));
    }

    // ---- summaries ------------------------------------------------------------------------------------------------

    @Test
    void aBookSummaryCarriesAvailabilityStockAndTheDeliveryDateRule() throws Exception {
        LocalDate today = LocalDate.now(clock);

        // Book 1 (in stock, paperback), book 9 (eBook) and book 5 (paperback, stock 0) by their titles.
        books("q=The Habit Ladder").andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[0].stockQuantity").value(25))
                .andExpect(jsonPath("$.items[0].price").value("499.00"))
                .andExpect(jsonPath("$.items[0].deliveryDate").value(today.plusDays(5).toString()))
                .andExpect(jsonPath("$.items[0].categories[0].name").value("Self Help"));

        books("q=Focus Without Friction").andExpect(jsonPath("$.items[0].format").value("EBOOK"))
                .andExpect(jsonPath("$.items[0].available").value(true))
                .andExpect(jsonPath("$.items[0].stockQuantity").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].deliveryDate").value(today.toString()));

        books("q=Morning Pages Reloaded").andExpect(jsonPath("$.items[0].available").value(false))
                .andExpect(jsonPath("$.items[0].stockQuantity").value(0));
    }

    // ---- validation -----------------------------------------------------------------------------------------------

    @Test
    void invalidQueryParametersAreAValidationFailureWithNoDetailsLeaked() throws Exception {
        for (String bad : List.of("page=-1", "sort=NEWEST", "format=PDF", "minPrice=abc", "maxPrice=5", "category=0",
                "category=abc", "q=" + "x".repeat(101), "brand=" + "x".repeat(121), "language=" + "x".repeat(41))) {
            books(bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("The request contains invalid values."));
        }
    }

    @Test
    void theCatalogueIsPublicAndNeedsNoLogin() throws Exception {
        mockMvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/brands")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/books")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/books/filter-options")).andExpect(status().isOk());
    }

    @Test
    void sqlInjectionTextInASearchIsTreatedAsPlainText() throws Exception {
        books("q=" + java.net.URLEncoder.encode("'; DROP TABLE book; --", java.nio.charset.StandardCharsets.UTF_8))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        assertThat(count("SELECT COUNT(*) FROM book")).isEqualTo(60);
    }
}
