package com.bookygeek.catalogue.controller;

import com.bookygeek.catalogue.dto.ReviewListResponse;
import com.bookygeek.catalogue.dto.ReviewRequest;
import com.bookygeek.catalogue.dto.ReviewResponse;
import com.bookygeek.catalogue.service.ReviewService;
import com.bookygeek.common.security.AuthenticatedCustomer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code listBookReviews} (public) and {@code createBookReview} (signed in, CSRF header) exactly as in
 * {@code api/openapi.yaml}.
 */
@RestController
@RequestMapping("/api/v1/books/{id:\\d{1,18}}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ReviewListResponse listBookReviews(@PathVariable long id) {
        return reviewService.list(id);
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createBookReview(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable long id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(id, customer.id(), request));
    }
}
