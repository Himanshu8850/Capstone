package com.bookygeek.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Body of {@code updateBasketItem}. */
public record UpdateBasketItemRequest(
        @NotNull(message = "Choose a quantity from 1 to 10.")
        @Min(value = 1, message = "Choose a quantity from 1 to 10.")
        @Max(value = 10, message = "Choose a quantity from 1 to 10.")
        Integer quantity) {
}
