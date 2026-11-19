package com.bookygeek.cart.dto;

/**
 * One change made while merging a guest basket: the book was skipped or its quantity reduced.
 * {@code UNAVAILABLE} = out of stock, {@code NOT_IN_CATALOGUE} = no longer listed, {@code QUANTITY_REDUCED} = capped
 * at min(10, stock).
 */
public record BasketAdjustment(long bookId, String title, Reason reason, int requestedQuantity, int addedQuantity) {

    public enum Reason {
        UNAVAILABLE, NOT_IN_CATALOGUE, QUANTITY_REDUCED
    }
}
