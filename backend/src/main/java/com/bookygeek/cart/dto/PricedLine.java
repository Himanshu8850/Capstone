package com.bookygeek.cart.dto;

import java.math.BigDecimal;

/** The input of {@code PricingService}: a unit price, a quantity and whether the book is an eBook. */
public record PricedLine(BigDecimal unitPrice, int quantity, boolean ebook) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
