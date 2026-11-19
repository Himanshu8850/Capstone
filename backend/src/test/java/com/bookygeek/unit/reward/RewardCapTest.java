package com.bookygeek.unit.reward;

import com.bookygeek.cart.dto.BasketLine;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.cart.service.PricingService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import com.bookygeek.order.dto.CheckoutQuote;
import com.bookygeek.order.service.CheckoutQuoteService;
import com.bookygeek.reward.service.RewardService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The redemption cap (T126, FR-RWD-02, FR-RWD-03, PL-02): {@code min(balance, floor(50% x (subtotal + tax +
 * delivery)))} with one point worth one rupee. Covers a balance below, equal to and above the cap, odd totals that
 * round down, a zero balance, and the checkout quote accepting exactly the cap and rejecting one point more with
 * {@code POINTS_LIMIT_EXCEEDED}.
 */
class RewardCapTest {

    private static final long CUSTOMER = 5L;

    private final PricingService pricing = new PricingService(new BookygeekProperties(
            new BookygeekProperties.Rewards(1000, 50),
            new BookygeekProperties.Tax(new BigDecimal("0.05")),
            new BookygeekProperties.Delivery(new BigDecimal("40.00")),
            new BookygeekProperties.Cancellation(48),
            List.of(new BookygeekProperties.PriceBand("Any", null, null))));

    private int max(int balance, String total) {
        return pricing.maxRedeemablePoints(balance, new BigDecimal(total));
    }

    private CheckoutQuote quote(int balance, String total, int points) {
        CartService cart = mock(CartService.class);
        RewardService rewards = mock(RewardService.class);
        BookSummary book = new BookSummary(1L, "Book", "Author", "Description", BookFormat.PAPERBACK, "Publisher",
                "English", "500.00", "/covers/1-front.svg", List.of(), true, 5, "2026-11-17");
        when(cart.getBasket(CUSTOMER)).thenReturn(new BasketResponse(List.of(new BasketLine(book, 1, "500.00")), 1,
                "500.00", "25.00", "40.00", total));
        when(rewards.getBalance(CUSTOMER)).thenReturn(balance);
        return new CheckoutQuoteService(cart, pricing, rewards).quote(CUSTOMER, points);
    }

    // ---- the cap ----------------------------------------------------------------------------------------------------

    @Test
    void aBalanceBelowTheCapIsTheMaximum() {
        assertThat(max(200, "1090.00")).isEqualTo(200); // cap 545
    }

    @Test
    void aBalanceEqualToTheCapIsTheMaximum() {
        assertThat(max(545, "1090.00")).isEqualTo(545);
    }

    @Test
    void aBalanceAboveTheCapIsLimitedToTheCap() {
        assertThat(max(1000, "1090.00")).isEqualTo(545);
        assertThat(max(Integer.MAX_VALUE, "1090.00")).isEqualTo(545);
    }

    @Test
    void anOddTotalRoundsTheCapDownToAWholePoint() {
        assertThat(max(1000, "1091.85")).isEqualTo(545); // 545.925
        assertThat(max(1000, "1091.99")).isEqualTo(545);
        assertThat(max(1000, "1092.00")).isEqualTo(546);
        assertThat(max(1000, "103.95")).isEqualTo(51); // eBook order, 51.975
        assertThat(max(1000, "0.99")).isZero();
        assertThat(max(1000, "1.99")).isZero();
        assertThat(max(1000, "2.00")).isEqualTo(1);
    }

    @Test
    void aZeroBalanceCannotRedeemAnything() {
        assertThat(max(0, "1090.00")).isZero();
    }

    @Test
    void aNegativeBalanceIsTreatedAsZero() {
        assertThat(max(-5, "1090.00")).isZero();
    }

    @Test
    void thePointsCapNeverReachesTheWholeTotalSoAPaymentMethodIsAlwaysNeeded() {
        for (String total : List.of("2.00", "103.95", "563.95", "1090.00", "99999.99")) {
            int cap = max(Integer.MAX_VALUE, total);
            assertThat(new BigDecimal(total).subtract(BigDecimal.valueOf(cap))).isPositive();
        }
    }

    // ---- accepted and rejected requests -----------------------------------------------------------------------------

    @Test
    void redeemingZeroPointsIsAllowedAndLeavesTheTotalUnchanged() {
        CheckoutQuote quote = quote(1000, "1090.00", 0);

        assertThat(quote.pointsToRedeem()).isZero();
        assertThat(quote.payableTotal()).isEqualTo("1090.00");
        assertThat(quote.maxRedeemablePoints()).isEqualTo(545);
        assertThat(quote.balance()).isEqualTo(1000);
    }

    @Test
    void redeemingExactlyTheCapIsAcceptedAndReducesThePayableTotalByOneRupeePerPoint() {
        CheckoutQuote quote = quote(1000, "1090.00", 545);

        assertThat(quote.pointsToRedeem()).isEqualTo(545);
        assertThat(quote.payableTotal()).isEqualTo("545.00");
    }

    @Test
    void redeemingOnePointMoreThanTheCapIsRejectedWithPointsLimitExceeded() {
        assertThatThrownBy(() -> quote(1000, "1090.00", 546))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.POINTS_LIMIT_EXCEEDED);
                    assertThat(e.getMessage()).contains("at most 545 points");
                });
    }

    @Test
    void redeemingExactlyTheBalanceWhenItIsBelowTheCapIsAcceptedAndOneMoreIsRejected() {
        assertThat(quote(200, "1090.00", 200).payableTotal()).isEqualTo("890.00");

        assertThatThrownBy(() -> quote(200, "1090.00", 201))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.POINTS_LIMIT_EXCEEDED));
    }

    @Test
    void aZeroBalanceRejectsAnyRedemptionAndSaysSo() {
        assertThatThrownBy(() -> quote(0, "1090.00", 1))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.POINTS_LIMIT_EXCEEDED);
                    assertThat(e.getMessage()).contains("at most 0 points");
                });
        assertThat(quote(0, "1090.00", 0).maxRedeemablePoints()).isZero();
    }

    @Test
    void aSingleRedeemablePointUsesTheSingularInTheMessage() {
        assertThatThrownBy(() -> quote(1000, "2.00", 2))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getMessage()).contains("at most 1 point on this order"));
    }
}
