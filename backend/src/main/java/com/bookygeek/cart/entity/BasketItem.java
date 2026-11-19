package com.bookygeek.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One line of a customer's basket. A basket is simply the set of a customer's lines (no header table); the price is
 * never stored, the live catalogue price is used. The primary key is {@code (customer_id, book_id)}, so a book can
 * appear once per customer; the database also checks {@code quantity BETWEEN 1 AND 10}.
 */
@Entity
@Table(name = "basket_item")
@IdClass(BasketItem.Key.class)
public class BasketItem {

    /** Composite primary key {@code (customer_id, book_id)}. */
    public static class Key implements Serializable {
        private long customerId;
        private long bookId;

        public Key() {
            // for JPA
        }

        public Key(long customerId, long bookId) {
            this.customerId = customerId;
            this.bookId = bookId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && customerId == key.customerId && bookId == key.bookId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(customerId, bookId);
        }
    }

    @Id
    @Column(name = "customer_id")
    private long customerId;

    @Id
    @Column(name = "book_id")
    private long bookId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BasketItem() {
        // for JPA
    }

    public BasketItem(long customerId, long bookId, int quantity, Instant updatedAt) {
        this.customerId = customerId;
        this.bookId = bookId;
        this.quantity = quantity;
        this.updatedAt = updatedAt;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getBookId() {
        return bookId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void changeQuantity(int quantity, Instant now) {
        this.quantity = quantity;
        this.updatedAt = now;
    }
}
