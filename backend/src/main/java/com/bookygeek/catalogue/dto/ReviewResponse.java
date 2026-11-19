package com.bookygeek.catalogue.dto;

import java.time.Instant;

/** One review; mirrors {@code Review} in {@code api/openapi.yaml}. {@code comment} may be null. */
public record ReviewResponse(long id, String reviewerName, int rating, String comment, Instant createdAt) {
}
