package com.bookygeek.catalogue.controller;

import com.bookygeek.catalogue.dto.BookDetail;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookPage;
import com.bookygeek.catalogue.dto.BookSort;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.dto.FilterOptions;
import com.bookygeek.catalogue.service.CatalogueService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code listBooks}, {@code getBookFilterOptions}, {@code getBook} and {@code listRelatedBooks} exactly as in
 * {@code api/openapi.yaml}. All are public. Every list query parameter is validated against the contract's limits, so
 * out-of-range input is a 400 {@code VALIDATION_FAILED}; an unknown or unlisted book id is a 404 {@code BOOK_NOT_FOUND}.
 */
@RestController
@RequestMapping("/api/v1/books")
public class BookController {

    private static final String MONEY = "^[0-9]+\\.[0-9]{2}$";

    private final CatalogueService catalogueService;

    public BookController(CatalogueService catalogueService) {
        this.catalogueService = catalogueService;
    }

    @GetMapping
    public BookPage listBooks(
            @RequestParam(required = false) @Min(value = 1, message = "Choose a valid category.") Long category,
            @RequestParam(required = false) @Size(max = 120, message = "Use at most 120 characters.") String brand,
            @RequestParam(required = false) @Size(max = 100, message = "Use at most 100 characters.") String q,
            @RequestParam(required = false) @Size(max = 120, message = "Use at most 120 characters.") String author,
            @RequestParam(required = false) @Size(max = 40, message = "Use at most 40 characters.") String language,
            @RequestParam(required = false) BookFormat format,
            @RequestParam(required = false) @Pattern(regexp = MONEY, message = "Enter an amount such as 199.00.") String minPrice,
            @RequestParam(required = false) @Pattern(regexp = MONEY, message = "Enter an amount such as 199.00.") String maxPrice,
            @RequestParam(required = false) BookSort sort,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "The page number cannot be negative.") int page) {
        return catalogueService.listBooks(category, brand, q, author, language, format,
                minPrice == null ? null : new BigDecimal(minPrice),
                maxPrice == null ? null : new BigDecimal(maxPrice), sort, page);
    }

    @GetMapping("/filter-options")
    public FilterOptions getBookFilterOptions() {
        return catalogueService.filterOptions();
    }

    @GetMapping("/{id:\\d{1,18}}")
    public BookDetail getBook(@PathVariable long id) {
        return catalogueService.getBook(id);
    }

    @GetMapping("/{id:\\d{1,18}}/related")
    public List<BookSummary> listRelatedBooks(@PathVariable long id) {
        return catalogueService.listRelatedBooks(id);
    }
}
