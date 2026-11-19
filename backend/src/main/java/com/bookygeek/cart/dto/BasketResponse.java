package com.bookygeek.cart.dto;

import java.util.List;

/**
 * The contract's {@code Basket}: priced lines plus the order totals. Amounts are {@code Money} strings with two
 * decimals; {@code itemCount} is the total number of units (the header badge).
 */
public record BasketResponse(
        List<BasketLine> items,
        int itemCount,
        String subtotal,
        String tax,
        String deliveryCharge,
        String total) {
}
