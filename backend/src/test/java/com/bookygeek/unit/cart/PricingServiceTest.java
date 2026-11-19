package com.bookygeek.unit.cart;

import com.bookygeek.cart.dto.PricedLine;
import com.bookygeek.cart.dto.Totals;
import com.bookygeek.cart.service.PricingService;
import com.bookygeek.common.props.BookygeekProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money rules (T083, approved CL-07): tax is 5% of the subtotal, once per order, HALF_UP to two decimals; delivery is
 * 40.00 when any physical book is present and 0.00 for an eBook-only (or empty) order; the reward cap is
 * {@code min(balance, floor(50% x (subtotal + tax + delivery)))}; every amount has scale 2.
 */
class PricingServiceTest {

    private final PricingService pricing = new PricingService(new BookygeekProperties(
            new BookygeekProperties.Rewards(1000, 50),
            new BookygeekProperties.Tax(new BigDecimal("0.05")),
            new BookygeekProperties.Delivery(new BigDecimal("40.00")),
            new BookygeekProperties.Cancellation(48),
            List.of(new BookygeekProperties.PriceBand("Any", null, null))));

    private static PricedLine physical(String price, int quantity) {
        return new PricedLine(new BigDecimal(price), quantity, false);
    }

    private static PricedLine ebook(String price, int quantity) {
        return new PricedLine(new BigDecimal(price), quantity, true);
    }

    @Test
    void physicalOrderPaysTaxOnTheSubtotalAndTheDeliveryCharge() {
        Totals totals = pricing.price(List.of(physical("500", 2)));

        assertThat(totals.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(totals.tax()).isEqualByComparingTo("50.00");
        assertThat(totals.deliveryCharge()).isEqualByComparingTo("40.00");
        assertThat(totals.total()).isEqualByComparingTo("1090.00");
    }

    @Test
    void eBookOnlyOrderHasNoDeliveryCharge() {
        Totals totals = pricing.price(List.of(ebook("99", 1), ebook("150", 2)));

        assertThat(totals.subtotal()).isEqualByComparingTo("399.00");
        assertThat(totals.deliveryCharge()).isEqualByComparingTo("0.00");
        assertThat(totals.total()).isEqualByComparingTo("418.95");
    }

    @Test
    void mixedOrderPaysDeliveryOncePerOrderNotPerLine() {
        Totals totals = pricing.price(List.of(physical("100", 1), physical("200", 1), ebook("99", 1)));

        assertThat(totals.deliveryCharge()).isEqualByComparingTo("40.00");
        assertThat(totals.subtotal()).isEqualByComparingTo("399.00");
    }

    @Test
    void taxIsCalculatedOncePerOrderAndNotPerLine() {
        // 0.05 x 10.10 = 0.505 -> 0.51 once; per line (0.05 x 5.05 = 0.2525 -> 0.25, twice) would give 0.50.
        Totals totals = pricing.price(List.of(ebook("5.05", 1), ebook("5.05", 1)));

        assertThat(totals.tax()).isEqualByComparingTo("0.51");
    }

    @Test
    void taxRoundsHalfUpAtTheBoundary() {
        assertThat(pricing.price(List.of(ebook("0.10", 1))).tax()).isEqualByComparingTo("0.01");   // 0.005 -> 0.01
        assertThat(pricing.price(List.of(ebook("0.09", 1))).tax()).isEqualByComparingTo("0.00");   // 0.0045 -> 0.00
        assertThat(pricing.price(List.of(ebook("0.11", 1))).tax()).isEqualByComparingTo("0.01");   // 0.0055 -> 0.01
        assertThat(pricing.price(List.of(ebook("199", 1))).tax()).isEqualByComparingTo("9.95");    // 9.95 exactly
        assertThat(pricing.price(List.of(ebook("449", 1))).tax()).isEqualByComparingTo("22.45");   // 22.45 exactly
        assertThat(pricing.price(List.of(ebook("1.50", 1))).tax()).isEqualByComparingTo("0.08");   // 0.075 -> 0.08
    }

    @Test
    void emptyOrderPricesToZeroEverywhere() {
        Totals totals = pricing.price(List.of());

        assertThat(totals.subtotal()).isEqualByComparingTo("0.00");
        assertThat(totals.tax()).isEqualByComparingTo("0.00");
        assertThat(totals.deliveryCharge()).isEqualByComparingTo("0.00");
        assertThat(totals.total()).isEqualByComparingTo("0.00");
    }

    @Test
    void everyAmountHasScaleTwo() {
        Totals totals = pricing.price(List.of(physical("499", 3), ebook("99", 1)));

        assertThat(totals.subtotal().scale()).isEqualTo(2);
        assertThat(totals.tax().scale()).isEqualTo(2);
        assertThat(totals.deliveryCharge().scale()).isEqualTo(2);
        assertThat(totals.total().scale()).isEqualTo(2);
        assertThat(PricingService.format(new BigDecimal("1090"))).isEqualTo("1090.00");
    }

    @Test
    void rewardCapIsHalfOfTheTotalRoundedDownAndNeverAboveTheBalance() {
        // total 1090.00 -> cap 545
        assertThat(pricing.maxRedeemablePoints(1000, new BigDecimal("1090.00"))).isEqualTo(545);
        assertThat(pricing.maxRedeemablePoints(545, new BigDecimal("1090.00"))).isEqualTo(545);
        assertThat(pricing.maxRedeemablePoints(544, new BigDecimal("1090.00"))).isEqualTo(544);
        // total 1089.99 -> 544.995 -> floor 544
        assertThat(pricing.maxRedeemablePoints(1000, new BigDecimal("1089.99"))).isEqualTo(544);
        // total 0.99 -> 0.495 -> 0
        assertThat(pricing.maxRedeemablePoints(1000, new BigDecimal("0.99"))).isZero();
        assertThat(pricing.maxRedeemablePoints(1000, new BigDecimal("1.00"))).isZero();
        assertThat(pricing.maxRedeemablePoints(1000, new BigDecimal("2.00"))).isEqualTo(1);
    }

    @Test
    void rewardCapIsZeroWithNoBalance() {
        assertThat(pricing.maxRedeemablePoints(0, new BigDecimal("1090.00"))).isZero();
    }
}
