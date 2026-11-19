package com.bookygeek.payment.entity;

import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One payment attempt (columns exactly as in {@code V1__init_schema.sql}). There is deliberately no column for card
 * numbers, CVV, expiry, name on card or UPI ID. A {@code FAILED} attempt has no order; every other state has one
 * (table CHECK). {@code (customer_id, idempotency_key)} is unique, which is what makes a repeated request safe.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 12, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 22)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // for JPA
    }

    private Payment(long customerId, Long orderId, PaymentMethod method, PaymentStatus status, BigDecimal amount,
            String failureCode, UUID idempotencyKey, Instant now) {
        this.customerId = customerId;
        this.orderId = orderId;
        this.method = method;
        this.status = status;
        this.amount = amount;
        this.failureCode = failureCode;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** A declined attempt: recorded with a reason code only, and never linked to an order. */
    public static Payment failed(long customerId, PaymentMethod method, BigDecimal amount, String failureCode,
            UUID idempotencyKey, Instant now) {
        return new Payment(customerId, null, method, PaymentStatus.FAILED, amount, failureCode, idempotencyKey, now);
    }

    /** The approved payment of a placed order. */
    public static Payment forOrder(long customerId, long orderId, PaymentMethod method, PaymentStatus status,
            BigDecimal amount, UUID idempotencyKey, Instant now) {
        return new Payment(customerId, orderId, method, status, amount, null, idempotencyKey, now);
    }

    public void changeStatus(PaymentStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }
}
