package com.bookygeek.payment.dto;

/**
 * State of a payment record. {@code PAID} (card, UPI), {@code PENDING_ON_DELIVERY} (COD), {@code FAILED} (a declined
 * attempt, never linked to an order), and after cancellation {@code REFUNDED} (simulated, no money moves) or
 * {@code CANCELLED} (COD).
 */
public enum PaymentStatus {
    PAID, PENDING_ON_DELIVERY, FAILED, REFUNDED, CANCELLED
}
