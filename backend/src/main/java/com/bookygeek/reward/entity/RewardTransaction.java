package com.bookygeek.reward.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Ledger row for a points movement caused by an order. Only {@code REDEEM} (at placement) and {@code RESTORE}
 * (at cancellation) exist; points are never earned. {@code UNIQUE (order_id, type)} makes a double restore impossible.
 */
@Entity
@Table(name = "reward_transaction")
public class RewardTransaction {

    /** The two ledger movements. */
    public enum Type {
        REDEEM, RESTORE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private long customerId;

    @Column(name = "order_id", nullable = false)
    private long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private Type type;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RewardTransaction() {
        // for JPA
    }

    public RewardTransaction(long customerId, long orderId, Type type, int points, Instant createdAt) {
        this.customerId = customerId;
        this.orderId = orderId;
        this.type = type;
        this.points = points;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getOrderId() {
        return orderId;
    }

    public Type getType() {
        return type;
    }

    public int getPoints() {
        return points;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
