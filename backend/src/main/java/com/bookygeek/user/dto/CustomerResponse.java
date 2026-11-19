package com.bookygeek.user.dto;

/** The {@code Customer} schema: public profile of the signed-in customer. No password data ever leaves the server. */
public record CustomerResponse(long id, String fullName, String email) {
}
