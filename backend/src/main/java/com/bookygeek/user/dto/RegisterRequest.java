package com.bookygeek.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code register}; mirrors {@code RegisterRequest} in {@code api/openapi.yaml}. The match between
 * {@code password} and {@code confirmPassword} is checked by {@code AuthService}. The record never prints the
 * passwords.
 */
public record RegisterRequest(
        @NotBlank(message = "Enter your full name.")
        @Size(max = 100, message = "Full name can have at most 100 characters.")
        String fullName,

        @NotBlank(message = "Enter your e-mail address.")
        @Size(max = 254, message = "Enter a valid e-mail address.")
        @Pattern(regexp = Patterns.EMAIL, message = "Enter a valid e-mail address.")
        String email,

        @NotNull(message = "Password must be 8 to 64 characters with at least one letter and one digit.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$",
                message = "Password must be 8 to 64 characters with at least one letter and one digit.")
        String password,

        @NotBlank(message = "Confirm your password.")
        String confirmPassword) {

    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=****, confirmPassword=****]";
    }
}
