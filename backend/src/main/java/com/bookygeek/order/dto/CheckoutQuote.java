package com.bookygeek.order.dto;

import java.util.List;

/**
 * The contract's {@code CheckoutQuote}. Amounts are {@code Money} strings; {@code balance}, {@code maxRedeemablePoints}
 * and {@code pointsToRedeem} are whole points (one point is worth one rupee). {@code payableTotal} is
 * {@code total - pointsToRedeem}. Nothing is stored when a quote is made.
 */
public record CheckoutQuote(
        String subtotal,
        String tax,
        String deliveryCharge,
        String total,
        int balance,
        int maxRedeemablePoints,
        int pointsToRedeem,
        String payableTotal,
        List<CheckoutIssue> issues) {
}
