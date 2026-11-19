package com.bookygeek.payment.dto;

/** A stored payment as other modules may see it: the order it belongs to, the method and the state. */
public record PaymentRecord(Long orderId, PaymentMethod method, PaymentStatus status) {
}
