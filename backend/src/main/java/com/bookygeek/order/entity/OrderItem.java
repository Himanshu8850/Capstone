package com.bookygeek.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line of an order: the book, the quantity (1..10) and the unit price at placement. The primary key is
 * {@code (order_id, book_id)}, so a book appears once per order.
 */
@Entity
@Table(name = "order_item")
@IdClass(OrderItem.Key.class)
public class OrderItem {

    /** Composite primary key {@code (order_id, book_id)}. */
    public static class Key implements Serializable {
        private long orderId;
        private long bookId;

        public Key() {
            // for JPA
        }

        public Key(long orderId, long bookId) {
            this.orderId = orderId;
            this.bookId = bookId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && orderId == key.orderId && bookId == key.bookId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(orderId, bookId);
        }
    }

    @Id
    @Column(name = "order_id")
    private long orderId;

    @Id
    @Column(name = "book_id")
    private long bookId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    protected OrderItem() {
        // for JPA
    }

    public OrderItem(long orderId, long bookId, int quantity, BigDecimal unitPrice) {
        this.orderId = orderId;
        this.bookId = bookId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getBookId() {
        return bookId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
