package com.bookygeek.cart.dto;

import java.math.BigDecimal;

/** Order amounts in rupees, all with scale 2: {@code total = subtotal + tax + deliveryCharge}. */
public record Totals(BigDecimal subtotal, BigDecimal tax, BigDecimal deliveryCharge, BigDecimal total) {
}
