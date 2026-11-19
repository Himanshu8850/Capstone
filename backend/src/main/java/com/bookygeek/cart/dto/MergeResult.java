package com.bookygeek.cart.dto;

/** The contract's {@code BasketWithReport}: the merged basket and what was adjusted on the way. */
public record MergeResult(BasketResponse basket, MergeReport report) {
}
