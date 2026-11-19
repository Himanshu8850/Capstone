package com.bookygeek.order.service;

import com.bookygeek.cart.dto.BasketLine;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.cart.service.PricingService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.order.dto.CheckoutIssue;
import com.bookygeek.order.dto.CheckoutQuote;
import com.bookygeek.reward.service.RewardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The checkout summary (FR-CHK-05/07, FR-BSK-08): the basket priced by {@link PricingService}, the reward balance, the
 * maximum redeemable points and the basket changes ({@code issues}) the customer must see before paying. Nothing is
 * stored. The basket keeps no price, so a price change is detected at placement by the {@code expectedPayableTotal}
 * check instead (R-14).
 */
@Service
@Transactional(readOnly = true)
public class CheckoutQuoteService {

    private final CartService cart;
    private final PricingService pricing;
    private final RewardService rewards;

    public CheckoutQuoteService(CartService cart, PricingService pricing, RewardService rewards) {
        this.cart = cart;
        this.pricing = pricing;
        this.rewards = rewards;
    }

    /**
     * Quotes the customer's basket with {@code pointsToRedeem} applied. An empty basket is {@code EMPTY_BASKET}; more
     * points than the balance or the 50% cap is {@code POINTS_LIMIT_EXCEEDED}.
     */
    public CheckoutQuote quote(long customerId, int pointsToRedeem) {
        BasketResponse basket = cart.getBasket(customerId);
        if (basket.items().isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_BASKET);
        }
        BigDecimal total = new BigDecimal(basket.total());
        int balance = rewards.getBalance(customerId);
        int max = pricing.maxRedeemablePoints(balance, total);
        if (pointsToRedeem > max) {
            throw new ApiException(ErrorCode.POINTS_LIMIT_EXCEEDED, pointsLimitMessage(max));
        }
        return new CheckoutQuote(basket.subtotal(), basket.tax(), basket.deliveryCharge(), basket.total(), balance, max,
                pointsToRedeem, PricingService.format(total.subtract(BigDecimal.valueOf(pointsToRedeem))),
                issuesOf(basket));
    }

    /** The message of {@code POINTS_LIMIT_EXCEEDED}: how many points may be redeemed on this order. */
    static String pointsLimitMessage(int max) {
        return max == 1 ? "You can redeem at most 1 point on this order."
                : "You can redeem at most " + max + " points on this order.";
    }

    private static List<CheckoutIssue> issuesOf(BasketResponse basket) {
        List<CheckoutIssue> issues = new ArrayList<>();
        for (BasketLine line : basket.items()) {
            BookSummary book = line.book();
            if (book.format() == BookFormat.EBOOK) {
                continue;
            }
            int stock = book.stockQuantity() == null ? 0 : book.stockQuantity();
            if (stock == 0) {
                issues.add(new CheckoutIssue(book.id(), book.title(), CheckoutIssue.Type.OUT_OF_STOCK,
                        "This book is out of stock. Remove it from your basket to continue."));
            } else if (line.quantity() > stock) {
                issues.add(new CheckoutIssue(book.id(), book.title(), CheckoutIssue.Type.QUANTITY_REDUCED,
                        "Only " + stock + (stock == 1 ? " copy is" : " copies are")
                                + " available. Reduce the quantity in your basket to continue."));
            }
        }
        return List.copyOf(issues);
    }
}
