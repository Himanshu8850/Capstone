package com.bookygeek.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of {@code login}; mirrors {@code LoginRequest} in {@code api/openapi.yaml}. Never prints the password. */
public record LoginRequest(
        @NotBlank(message = "Enter your e-mail address.")
        @Size(max = 254, message = "Enter a valid e-mail address.")
        @Pattern(regexp = Patterns.EMAIL, message = "Enter a valid e-mail address.")
        String email,

        @NotBlank(message = "Enter your password.")
        @Size(max = 64, message = "Password can have at most 64 characters.")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****]";
    }
}
