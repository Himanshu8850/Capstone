package com.bookygeek.order.dto;

import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentStatus;
import com.bookygeek.user.dto.AddressResponse;

import java.time.Instant;
import java.util.List;

/**
 * The contract's {@code Order}. {@code cancellableUntil} is always serialised, as {@code null} once the order is
 * cancelled. The address is the snapshot taken at placement (it has no id).
 */
public record OrderResponse(
        long id,
        String reference,
        OrderStatus status,
        Instant placedAt,
        List<OrderItemResponse> items,
        AddressResponse address,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String subtotal,
        String tax,
        String deliveryCharge,
        int pointsRedeemed,
        String payableTotal,
        boolean cancellable,
        Instant cancellableUntil) {
}
