package com.bookygeek.order.dto;

import com.bookygeek.catalogue.dto.BookSummary;

/** The contract's {@code OrderItem}: the book, the quantity and the price snapshot taken at placement. */
public record OrderItemResponse(BookSummary book, int quantity, String unitPrice, String lineTotal) {
}
