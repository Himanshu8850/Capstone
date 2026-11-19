package com.bookygeek.cart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body of {@code previewBasket} and {@code mergeGuestBasket}: 1 to 100 lines. */
public record BasketLinesRequest(
        @NotNull(message = "Send at least one item.")
        @Size(min = 1, max = 100, message = "Send between 1 and 100 items.")
        List<@Valid @NotNull(message = "Send a valid item.") BasketLineRequest> items) {
}
