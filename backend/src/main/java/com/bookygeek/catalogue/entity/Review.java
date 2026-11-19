package com.bookygeek.catalogue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One customer's rating and optional comment on one book (columns exactly as in {@code V2__review.sql}). A customer
 * reviews a book once; a review is never edited or deleted.
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false, updatable = false)
    private long bookId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;

    @Column(name = "rating", nullable = false, updatable = false)
    private int rating;

    @Column(name = "comment", length = 100, updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
        // for JPA
    }

    public Review(long bookId, long customerId, int rating, String comment, Instant createdAt) {
        this.bookId = bookId;
        this.customerId = customerId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getBookId() {
        return bookId;
    }

    public long getCustomerId() {
        return customerId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
