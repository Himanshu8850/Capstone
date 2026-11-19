package com.bookygeek.contract;

import com.bookygeek.integration.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Validates the real responses of {@code listCategories}, {@code listBrands}, {@code listBooks},
 * {@code getBookFilterOptions}, {@code getBook} and {@code listRelatedBooks} against {@code api/openapi.yaml} (T065,
 * T077): declared status codes, body schemas (including the eBook/physical-book stock rule of {@code BookSummary} and
 * {@code BookDetail}) and the {@code ApiError} shape of the 400 and 404 responses.
 */
class CatalogueContractTest extends IntegrationTestBase {

    private MvcResult call(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn();
    }

    private static void check(String operationId, MvcResult result) throws Exception {
        ContractSupport.assertResponse(operationId, result.getResponse().getStatus(),
                result.getResponse().getContentAsString());
    }

    @Test
    void listCategoriesMatchesTheContract() throws Exception {
        MvcResult result = call("/api/v1/categories");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        check("listCategories", result);
    }

    @Test
    void listBrandsMatchesTheContract() throws Exception {
        MvcResult result = call("/api/v1/brands");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        check("listBrands", result);
    }

    @Test
    void getBookFilterOptionsMatchesTheContract() throws Exception {
        MvcResult result = call("/api/v1/books/filter-options");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        check("getBookFilterOptions", result);
    }

    @Test
    void listBooksFirstPageMatchesTheContract() throws Exception {
        MvcResult result = call("/api/v1/books");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("\"pageSize\":12");
        check("listBooks", result);
    }

    @Test
    void listBooksCoversEveryFormatAndTheOutOfStockCase() throws Exception {
        // Page through the whole seeded catalogue so paperback, hardcover, eBook and the zero-stock book are all checked.
        for (int page = 0; page < 5; page++) {
            MvcResult result = call("/api/v1/books?sort=TITLE_ASC&page=" + page);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            check("listBooks", result);
        }
    }

    @Test
    void listBooksEmptyResultAndFilteredResultMatchTheContract() throws Exception {
        check("listBooks", call("/api/v1/books?q=zzzz-no-such-book"));
        check("listBooks", call("/api/v1/books?category=2&language=English&format=PAPERBACK"
                + "&minPrice=200.00&maxPrice=999.99&sort=PRICE_DESC&brand=Lantern%20Books"));
    }

    @Test
    void listBooksValidationFailedMatchesTheApiErrorSchema() throws Exception {
        MvcResult result = call("/api/v1/books?page=-1");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        check("listBooks", result);
        assertThat(result.getResponse().getContentAsString()).contains("\"fieldErrors\"");
    }

    @Test
    void theContractCheckRejectsAnEbookWithStockAndAPhysicalBookWithoutIt() {
        String ebookWithStock = book("EBOOK", "5");
        assertThatThrownBy(() -> ContractSupport.assertResponse("listBooks", 200, page(ebookWithStock)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("must match exactly one of");

        String physicalWithoutStock = book("PAPERBACK", "null");
        assertThatThrownBy(() -> ContractSupport.assertResponse("listBooks", 200, page(physicalWithoutStock)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("must match exactly one of");

        ContractSupport.assertResponse("listBooks", 200, page(book("EBOOK", "null")));
        ContractSupport.assertResponse("listBooks", 200, page(book("PAPERBACK", "0")));
    }

    @Test
    void theContractCheckRejectsMissingAndUndeclaredBookProperties() {
        String missing = page(book("EBOOK", "null").replace("\"price\":\"199.00\",", ""));
        assertThatThrownBy(() -> ContractSupport.assertResponse("listBooks", 200, missing))
                .hasMessageContaining("missing required property 'price'");

        String leaked = page(book("EBOOK", "null").replace("\"id\":1,", "\"id\":1,\"listed\":true,"));
        assertThatThrownBy(() -> ContractSupport.assertResponse("listBooks", 200, leaked))
                .hasMessageContaining("listed: property is not declared");

        String wrongPageSize = page(book("EBOOK", "null")).replace("\"pageSize\":12", "\"pageSize\":20");
        assertThatThrownBy(() -> ContractSupport.assertResponse("listBooks", 200, wrongPageSize))
                .hasMessageContaining("is not one of");
    }

    @Test
    void getBookMatchesTheContractForEveryFormatAndTheOutOfStockCase() throws Exception {
        // 1 paperback, 11 hardcover, 13 eBook, 5 physical with no stock, 54 with two categories.
        for (int id : new int[] {1, 11, 13, 5, 54}) {
            MvcResult result = call("/api/v1/books/" + id);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            check("getBook", result);
            assertThat(result.getResponse().getContentAsString()).contains("\"breadcrumb\":[{")
                    .contains("\"backCoverUrl\":");
        }
    }

    @Test
    void getBookNotFoundMatchesTheApiErrorSchema() throws Exception {
        MvcResult result = call("/api/v1/books/9999");
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        check("getBook", result);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"BOOK_NOT_FOUND\"");
    }

    @Test
    void listRelatedBooksMatchesTheContractWithAndWithoutResults() throws Exception {
        for (int id : new int[] {1, 13, 54}) {
            MvcResult result = call("/api/v1/books/" + id + "/related");
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            check("listRelatedBooks", result);
            assertThat(result.getResponse().getContentAsString()).startsWith("[{");
        }
        // Book 39 is the only Drama book, so the list is empty.
        MvcResult none = call("/api/v1/books/39/related");
        assertThat(none.getResponse().getContentAsString()).isEqualTo("[]");
        check("listRelatedBooks", none);
    }

    @Test
    void listRelatedBooksNotFoundMatchesTheApiErrorSchema() throws Exception {
        MvcResult result = call("/api/v1/books/9999/related");
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        check("listRelatedBooks", result);
    }

    @Test
    void theContractCheckRejectsABookDetailWithoutABreadcrumbOrWithTheWrongStockRule() {
        String detail = detail("EBOOK", "null");
        ContractSupport.assertResponse("getBook", 200, detail);

        assertThatThrownBy(() -> ContractSupport.assertResponse("getBook", 200,
                detail.replace(",\"breadcrumb\":[{\"id\":2,\"name\":\"Non-Fiction\"}]", "")))
                .hasMessageContaining("missing required property 'breadcrumb'");
        assertThatThrownBy(() -> ContractSupport.assertResponse("getBook", 200, detail("EBOOK", "5")))
                .isInstanceOf(AssertionError.class).hasMessageContaining("must match exactly one of");
        assertThatThrownBy(() -> ContractSupport.assertResponse("listRelatedBooks", 200,
                "{\"items\":[" + book("EBOOK", "null") + "]}"))
                .isInstanceOf(AssertionError.class);
    }

    private static String detail(String format, String stock) {
        return book(format, stock).replaceAll("}$", ",\"backCoverUrl\":null,"
                + "\"breadcrumb\":[{\"id\":2,\"name\":\"Non-Fiction\"}]}");
    }
    private static String book(String format, String stock) {
        return "{\"id\":1,\"title\":\"T\",\"author\":\"A\",\"description\":\"D\",\"format\":\"" + format + "\","
                + "\"publisher\":\"P\",\"language\":\"English\",\"price\":\"199.00\",\"frontCoverUrl\":\"/c.svg\","
                + "\"categories\":[{\"id\":14,\"name\":\"Self Help\"}],\"available\":true,\"stockQuantity\":" + stock
                + ",\"deliveryDate\":\"2026-11-17\"}";
    }

    private static String page(String book) {
        return "{\"items\":[" + book + "],\"page\":0,\"pageSize\":12,\"totalItems\":1,\"totalPages\":1}";
    }
}
