package com.bookygeek.catalogue.dto;

import java.math.BigDecimal;
import java.util.Set;

/**
 * The validated filters of one {@code listBooks} request. A {@code null} field means "no filter". {@code categoryIds}
 * is the already resolved subtree of the requested category (an empty set matches no book); {@code searchTerm} is
 * trimmed and lower-cased.
 */
public record BookQuery(
        Set<Long> categoryIds,
        String brand,
        String searchTerm,
        String author,
        String language,
        BookFormat format,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BookSort sort,
        int page) {

    public static final int PAGE_SIZE = 12;
}
