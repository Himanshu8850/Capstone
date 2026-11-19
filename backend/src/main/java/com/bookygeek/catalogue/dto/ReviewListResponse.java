package com.bookygeek.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body of {@code listBookReviews}; mirrors {@code ReviewList} in {@code api/openapi.yaml}. {@code averageRating} is
 * rounded HALF_UP to one decimal and is null when there are no reviews; {@code reviews} are newest first.
 */
public record ReviewListResponse(BigDecimal averageRating, int reviewCount, List<ReviewResponse> reviews) {
}
