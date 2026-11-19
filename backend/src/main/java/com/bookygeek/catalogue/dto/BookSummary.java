package com.bookygeek.catalogue.dto;

import com.bookygeek.category.dto.CategoryRef;

import java.util.List;

/** The contract's {@code BookSummary}. {@code stockQuantity} is always serialised, as {@code null} for an eBook. */
public record BookSummary(
        long id,
        String title,
        String author,
        String description,
        BookFormat format,
        String publisher,
        String language,
        String price,
        String frontCoverUrl,
        List<CategoryRef> categories,
        boolean available,
        Integer stockQuantity,
        String deliveryDate) {
}
