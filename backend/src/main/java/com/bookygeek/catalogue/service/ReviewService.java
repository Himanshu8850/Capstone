package com.bookygeek.catalogue.service;

import com.bookygeek.catalogue.dto.ReviewListResponse;
import com.bookygeek.catalogue.dto.ReviewRequest;
import com.bookygeek.catalogue.dto.ReviewResponse;
import com.bookygeek.catalogue.entity.Book;
import com.bookygeek.catalogue.entity.Review;
import com.bookygeek.catalogue.repository.BookRepository;
import com.bookygeek.catalogue.repository.ReviewRepository;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.user.service.AuthService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;

/**
 * Book reviews (AMD-002): the public list with its average, and one review per customer per book. There is no edit
 * and no delete. The reviewer's name comes from the User module's public service, never from its repository.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviews;
    private final BookRepository books;
    private final AuthService users;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, BookRepository books, AuthService users, Clock clock) {
        this.reviews = reviews;
        this.books = books;
        this.users = users;
        this.clock = clock;
    }

    /** Reviews of a listed book, newest first, with the average rounded HALF_UP to one decimal (null when none). */
    public ReviewListResponse list(long bookId) {
        requireListedBook(bookId);
        List<Review> rows = reviews.findByBookIdOrderByCreatedAtDescIdDesc(bookId);
        BigDecimal average = null;
        if (!rows.isEmpty()) {
            long sum = rows.stream().mapToLong(Review::getRating).sum();
            average = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(rows.size()), 1, RoundingMode.HALF_UP);
        }
        return new ReviewListResponse(average, rows.size(), rows.stream().map(this::toResponse).toList());
    }

    /** Adds the signed-in customer's review. A second review of the same book is {@code ALREADY_REVIEWED}. */
    @Transactional
    public ReviewResponse create(long bookId, long customerId, ReviewRequest request) {
        requireListedBook(bookId);
        if (reviews.existsByBookIdAndCustomerId(bookId, customerId)) {
            throw new ApiException(ErrorCode.ALREADY_REVIEWED);
        }
        try {
            Review saved = reviews.saveAndFlush(
                    new Review(bookId, customerId, request.rating(), request.comment(), clock.instant()));
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // Two requests of the same customer raced past the check above; the UNIQUE (book_id, customer_id) won.
            throw new ApiException(ErrorCode.ALREADY_REVIEWED);
        }
    }

    private void requireListedBook(long bookId) {
        books.findById(bookId).filter(Book::isListed)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND));
    }

    private ReviewResponse toResponse(Review review) {
        return new ReviewResponse(review.getId(), users.getCurrentCustomer(review.getCustomerId()).fullName(),
                review.getRating(), review.getComment(), review.getCreatedAt());
    }
}
