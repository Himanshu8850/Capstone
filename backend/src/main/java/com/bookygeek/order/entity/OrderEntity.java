package com.bookygeek.order.entity;

import com.bookygeek.order.dto.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A placed order (table {@code orders}; "order" is a reserved word). The delivery address is a snapshot in the
 * {@code ship_*} columns and the amounts are a snapshot of the pricing at placement, so later edits to an address,
 * a price or the stock never change an order. The display reference {@code BG-000002} is derived from the id.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private OrderStatus status;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "ship_full_name", length = 100, updatable = false)
    private String shipFullName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ship_phone", length = 10, updatable = false)
    private String shipPhone;

    @Column(name = "ship_line1", length = 150, updatable = false)
    private String shipLine1;

    @Column(name = "ship_line2", length = 150, updatable = false)
    private String shipLine2;

    @Column(name = "ship_city", length = 80, updatable = false)
    private String shipCity;

    @Column(name = "ship_state", length = 60, updatable = false)
    private String shipState;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ship_pin_code", length = 6, updatable = false)
    private String shipPinCode;

    @Column(name = "ship_country", length = 56, updatable = false)
    private String shipCountry;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal subtotal;

    @Column(name = "tax", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal tax;

    @Column(name = "delivery_charge", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal deliveryCharge;

    @Column(name = "points_redeemed", nullable = false, updatable = false)
    private int pointsRedeemed;

    @Column(name = "payable_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal payableTotal;

    protected OrderEntity() {
        // for JPA
    }

    /** A new {@code PLACED} order with its address (null for e-books only) and amount snapshots. */
    public OrderEntity(long customerId, Instant placedAt, Shipping shipping, BigDecimal subtotal, BigDecimal tax,
            BigDecimal deliveryCharge, int pointsRedeemed, BigDecimal payableTotal) {
        this.customerId = customerId;
        this.status = OrderStatus.PLACED;
        this.placedAt = placedAt;
        if (shipping != null) {
            this.shipFullName = shipping.fullName();
            this.shipPhone = shipping.phone();
            this.shipLine1 = shipping.line1();
            this.shipLine2 = shipping.line2();
            this.shipCity = shipping.city();
            this.shipState = shipping.state();
            this.shipPinCode = shipping.pinCode();
            this.shipCountry = shipping.country();
        }
        this.subtotal = subtotal;
        this.tax = tax;
        this.deliveryCharge = deliveryCharge;
        this.pointsRedeemed = pointsRedeemed;
        this.payableTotal = payableTotal;
    }

    /** The one status transition: PLACED to CANCELLED. The caller has already checked the rules and holds the row lock. */
    public void cancel(Instant at) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = at;
    }

    /** The delivery address copied into an order. */
    public record Shipping(String fullName, String phone, String line1, String line2, String city, String state,
            String pinCode, String country) {
    }

    /** The display reference, for example {@code BG-000002}. */
    public String getReference() {
        return String.format("BG-%06d", id);
    }

    /** The delivery address snapshot, or {@code null} for an order of e-books only. */
    public Shipping shipping() {
        if (shipLine1 == null) {
            return null;
        }
        return new Shipping(shipFullName, shipPhone, shipLine1, shipLine2, shipCity, shipState, shipPinCode,
                shipCountry);
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public BigDecimal getDeliveryCharge() {
        return deliveryCharge;
    }

    public int getPointsRedeemed() {
        return pointsRedeemed;
    }

    public BigDecimal getPayableTotal() {
        return payableTotal;
    }
}
