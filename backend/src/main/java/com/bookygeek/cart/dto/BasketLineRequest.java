package com.bookygeek.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One line sent by a guest or merged at login: {@code BasketLineRequest} in the contract. */
public record BasketLineRequest(
        @NotNull(message = "Choose a book.")
        @Min(value = 1, message = "Choose a valid book.")
        Long bookId,

        @NotNull(message = "Choose a quantity from 1 to 10.")
        @Min(value = 1, message = "Choose a quantity from 1 to 10.")
        @Max(value = 10, message = "Choose a quantity from 1 to 10.")
        Integer quantity) {
}
