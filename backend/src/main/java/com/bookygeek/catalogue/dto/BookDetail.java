package com.bookygeek.catalogue.dto;

import com.bookygeek.category.dto.CategoryRef;

import java.util.List;

/**
 * The contract's {@code BookDetail}: every {@link BookSummary} field plus the back cover, the breadcrumb (the
 * category path from the top-level category to the book's category) and the writer's short biography (null when the
 * author has none). {@code stockQuantity} is always serialised, as {@code null} for an eBook.
 */
public record BookDetail(
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
        String deliveryDate,
        String backCoverUrl,
        List<CategoryRef> breadcrumb,
        String authorBio) {

    public static BookDetail of(BookSummary summary, String backCoverUrl, List<CategoryRef> breadcrumb,
            String authorBio) {
        return new BookDetail(summary.id(), summary.title(), summary.author(), summary.description(),
                summary.format(), summary.publisher(), summary.language(), summary.price(),
                summary.frontCoverUrl(), summary.categories(), summary.available(), summary.stockQuantity(),
                summary.deliveryDate(), backCoverUrl, breadcrumb, authorBio);
    }
}