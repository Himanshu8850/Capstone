package com.bookygeek.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The contract's {@code Address}: the {@link AddressRequest} fields plus {@code id}. An order's address uses the same
 * shape without an id (the contract's {@code AddressInput}), so {@code id} is omitted when it is {@code null}, and
 * so is an empty {@code addressLine2}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressResponse(
        Long id,
        String fullName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pinCode,
        String country) {
}
