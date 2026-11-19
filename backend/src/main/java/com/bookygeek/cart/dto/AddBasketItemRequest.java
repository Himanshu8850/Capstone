package com.bookygeek.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of {@code addBasketItem}; {@code quantity} defaults to 1. */
public record AddBasketItemRequest(
        @NotNull(message = "Choose a book.")
        @Min(value = 1, message = "Choose a valid book.")
        Long bookId,

        @Min(value = 1, message = "Choose a quantity from 1 to 10.")
        @Max(value = 10, message = "Choose a quantity from 1 to 10.")
        Integer quantity) {

    public int quantityOrDefault() {
        return quantity == null ? 1 : quantity;
    }
}
