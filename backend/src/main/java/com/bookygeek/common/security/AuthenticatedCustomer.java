package com.bookygeek.common.security;

/** The signed-in customer, available as the authentication principal. There is a single role (customer). */
public record AuthenticatedCustomer(long id) {
}
