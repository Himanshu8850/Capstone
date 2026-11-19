package com.bookygeek.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of {@code quoteCheckout}: the whole reward points the customer wants to redeem (0 for none). */
public record CheckoutQuoteRequest(
        @NotNull(message = "Enter the points to redeem (0 for none).")
        @Min(value = 0, message = "Points to redeem cannot be negative.")
        Integer pointsToRedeem) {
}
