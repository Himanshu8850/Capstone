package com.bookygeek.order.dto;

/** Order status: only {@code PLACED} and {@code CANCELLED} exist; the one transition is PLACED to CANCELLED. */
public enum OrderStatus {
    PLACED, CANCELLED
}
