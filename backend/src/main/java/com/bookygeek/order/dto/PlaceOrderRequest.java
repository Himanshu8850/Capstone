package com.bookygeek.order.dto;

import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentRequest;
import com.bookygeek.user.dto.AddressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Body of {@code placeOrder}; mirrors the contract's {@code PlaceOrderRequest}. Exactly one of {@code addressId} and
 * {@code address} is sent, and {@code payment} must match {@code paymentMethod}; both rules are checked by
 * {@code OrderService} before anything is locked. The record never prints payment details (they are masked).
 */
public record PlaceOrderRequest(
        @Min(value = 1, message = "Choose a valid address.")
        Long addressId,

        @Valid
        AddressRequest address,

        @NotNull(message = "Choose a payment method.")
        PaymentMethod paymentMethod,

        @Valid
        PaymentRequest payment,

        @NotNull(message = "Enter the points to redeem (0 for none).")
        @Min(value = 0, message = "Points to redeem cannot be negative.")
        Integer pointsToRedeem,

        @NotNull(message = "The expected payable total is required.")
        @Pattern(regexp = "^[0-9]+\\.[0-9]{2}$", message = "Enter an amount such as 1090.00.")
        String expectedPayableTotal) {
}
