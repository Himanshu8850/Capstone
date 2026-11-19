package com.bookygeek.catalogue.dto;

import java.math.BigDecimal;

/**
 * A listed book read under a row lock for an order: its price and stock are the committed values nobody else can
 * change until the order's transaction ends. {@code stock} is {@code null} for an eBook.
 */
public record PurchaseBook(long id, String title, BookFormat format, BigDecimal price, Integer stock) {

    public boolean ebook() {
        return format == BookFormat.EBOOK;
    }
}
