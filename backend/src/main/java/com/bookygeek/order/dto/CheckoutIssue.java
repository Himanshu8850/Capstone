package com.bookygeek.order.dto;

/** The contract's {@code CheckoutIssue}: a basket change the customer must see before paying. */
public record CheckoutIssue(long bookId, String title, Type type, String message) {

    /** The issue kinds of the contract. */
    public enum Type {
        OUT_OF_STOCK, QUANTITY_REDUCED, PRICE_CHANGED, NOT_IN_CATALOGUE
    }
}
