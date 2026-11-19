package com.bookygeek.order.dto;

/** The outcome of {@code placeOrder}: the order and whether it was an earlier result replayed for the same key. */
public record PlaceOrderResult(OrderResponse order, boolean replayed) {
}
