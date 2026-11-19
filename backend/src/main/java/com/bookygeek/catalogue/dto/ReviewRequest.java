package com.bookygeek.catalogue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of {@code createBookReview}; mirrors {@code ReviewInput} in {@code api/openapi.yaml}. */
public record ReviewRequest(
        @NotNull(message = "Select a rating from 1 to 5.")
        @Min(value = 1, message = "Select a rating from 1 to 5.")
        @Max(value = 5, message = "Select a rating from 1 to 5.")
        Integer rating,

        @Size(max = 100, message = "Comment must be 100 characters or fewer.")
        String comment) {
}
