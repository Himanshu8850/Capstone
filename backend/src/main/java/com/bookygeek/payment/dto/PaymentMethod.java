package com.bookygeek.payment.dto;

/** The four payment methods of the contract's {@code PaymentMethod}. */
public enum PaymentMethod {
    CREDIT_CARD, DEBIT_CARD, UPI, COD;

    public boolean isCard() {
        return this == CREDIT_CARD || this == DEBIT_CARD;
    }
}
