package com.bookygeek.catalogue.dto;

import java.util.List;

/** The contract's {@code BookPage}: one page of at most 12 books. */
public record BookPage(List<BookSummary> items, int page, int pageSize, long totalItems, int totalPages) {
}
