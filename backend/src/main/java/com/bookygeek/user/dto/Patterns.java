package com.bookygeek.user.dto;

/** Shared validation patterns of the user DTOs. */
final class Patterns {

    /**
     * A well-formed e-mail: something, one at-sign, something, a dot, something; surrounding spaces are allowed
     * because the server trims the value. A blank value matches so that only {@code @NotBlank} reports it.
     */
    static final String EMAIL = "^\\s*([^\\s@]+@[^\\s@]+\\.[^\\s@]+)?\\s*$";

    private Patterns() {
    }
}
