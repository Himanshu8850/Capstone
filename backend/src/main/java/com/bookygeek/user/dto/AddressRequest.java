package com.bookygeek.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The contract's {@code AddressInput}: body of {@code createAddress} and {@code updateAddress}, and the
 * {@code address} of {@code placeOrder}. A missing country means India. For India the state must be in the
 * {@code listStates} list; that check is made by {@code AddressService} because it needs the reference data.
 */
public record AddressRequest(
        @NotBlank(message = "Enter the full name.")
        @Size(max = 100, message = "Full name can have at most 100 characters.")
        String fullName,

        @NotBlank(message = "Enter the phone number.")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits.")
        String phone,

        @NotBlank(message = "Enter address line 1.")
        @Size(max = 150, message = "Address line 1 can have at most 150 characters.")
        String addressLine1,

        @Size(max = 150, message = "Address line 2 can have at most 150 characters.")
        String addressLine2,

        @NotBlank(message = "Enter the city.")
        @Size(max = 80, message = "City can have at most 80 characters.")
        String city,

        @NotBlank(message = "Choose the state.")
        @Size(max = 60, message = "State can have at most 60 characters.")
        String state,

        @NotBlank(message = "Enter the PIN code.")
        @Pattern(regexp = "^[0-9]{6}$", message = "PIN code must be exactly 6 digits.")
        String pinCode,

        @Size(max = 56, message = "Country can have at most 56 characters.")
        String country) {

    public static final String DEFAULT_COUNTRY = "India";

    /** The country to store: the entered one, trimmed, or India when none was given. */
    public String countryOrDefault() {
        return country == null || country.isBlank() ? DEFAULT_COUNTRY : country.trim();
    }
}
