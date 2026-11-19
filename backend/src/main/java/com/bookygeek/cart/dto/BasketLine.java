package com.bookygeek.cart.dto;

import com.bookygeek.catalogue.dto.BookSummary;

/** The contract's {@code BasketLine}: the book as shown in the catalogue, the quantity (1..10) and the line total. */
public record BasketLine(BookSummary book, int quantity, String lineTotal) {
}
