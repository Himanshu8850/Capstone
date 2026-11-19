package com.bookygeek.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The {@code payment} object of {@code placeOrder} (the contract's {@code PaymentDetails}): {@code card} for the two
 * card methods, {@code upi} for UPI, nothing for COD. These details exist only in memory for the length of one
 * request. They are never persisted, never logged and never echoed: every {@code toString} masks them and no payment
 * column can hold them.
 */
public record PaymentRequest(@Valid Card card, @Valid Upi upi) {

    /** Card details (PL-06): number {@code XXXX-XXXX-XXXX-XXXX}, 3-digit CVV, name 2 to 60 characters, MM/YYYY. */
    public record Card(
            @NotNull(message = "Enter the card number.")
            @Pattern(regexp = "^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$",
                    message = "Card number must look like 1234-5678-9012-3456.")
            String number,

            @NotNull(message = "Enter the name on the card.")
            @Size(min = 2, max = 60, message = "Name on card must be 2 to 60 characters.")
            String nameOnCard,

            @NotNull(message = "Enter the CVV.")
            @Pattern(regexp = "^[0-9]{3}$", message = "CVV must be exactly 3 digits.")
            String cvv,

            @NotNull(message = "Enter the expiry date.")
            @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{4}$", message = "Expiry must be in the form MM/YYYY.")
            String expiry) {

        @Override
        public String toString() {
            return "Card[****]";
        }
    }

    /** A simulated UPI ID {@code name@handle}. */
    public record Upi(
            @NotNull(message = "Enter your UPI ID.")
            @Pattern(regexp = "^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$",
                    message = "Enter a UPI ID such as name@bank.")
            String upiId) {

        @Override
        public String toString() {
            return "Upi[****]";
        }
    }
}
