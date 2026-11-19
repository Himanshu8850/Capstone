package com.bookygeek.order.dto;

import java.time.Instant;
import java.util.List;

/** The contract's {@code OrderSummary}: one row of My Orders. */
public record OrderSummaryResponse(
        long id,
        String reference,
        OrderStatus status,
        Instant placedAt,
        List<OrderItemResponse> items,
        String payableTotal) {
}
