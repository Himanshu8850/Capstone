package com.bookygeek.common.props;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business configuration under the {@code bookygeek.*} prefix. Values are validated when the object is created, so an
 * invalid configuration stops the application at start-up instead of corrupting prices or reward limits.
 *
 * <p>Pricing rules (approved CL-07): tax is {@code tax.rate} of the discounted subtotal, once per order, HALF_UP to two
 * decimals; delivery is {@code delivery.charge} per order when any physical book is present. Reward redemption is
 * limited to {@code rewards.cap-percent} percent of subtotal + tax + delivery.
 */
@ConfigurationProperties(prefix = "bookygeek")
public record BookygeekProperties(
        @DefaultValue Rewards rewards,
        @DefaultValue Tax tax,
        @DefaultValue Delivery delivery,
        @DefaultValue Cancellation cancellation,
        List<PriceBand> priceBands) {

    public BookygeekProperties {
        priceBands = priceBands == null ? List.of() : List.copyOf(priceBands);
        if (priceBands.isEmpty()) {
            throw new IllegalArgumentException("bookygeek.price-bands must define at least one price band");
        }
    }

    /** Reward points: one point is worth one rupee. */
    public record Rewards(@DefaultValue("1000") int initialPoints, @DefaultValue("50") int capPercent) {
        public Rewards {
            if (initialPoints < 0) {
                throw new IllegalArgumentException("bookygeek.rewards.initial-points must not be negative");
            }
            if (capPercent < 1 || capPercent > 100) {
                throw new IllegalArgumentException("bookygeek.rewards.cap-percent must be between 1 and 100");
            }
        }
    }

    /** Tax rate as a fraction (0.05 is 5%). */
    public record Tax(@DefaultValue("0.05") BigDecimal rate) {
        public Tax {
            if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("bookygeek.tax.rate must be between 0 and 1");
            }
        }
    }

    /** Delivery charge in rupees per order that contains a physical book. */
    public record Delivery(@DefaultValue("40.00") BigDecimal charge) {
        public Delivery {
            if (charge == null || charge.signum() < 0) {
                throw new IllegalArgumentException("bookygeek.delivery.charge must not be negative");
            }
        }
    }

    /** Length of the order cancellation window, measured from order placement. */
    public record Cancellation(@DefaultValue("48") int windowHours) {
        public Cancellation {
            if (windowHours < 1) {
                throw new IllegalArgumentException("bookygeek.cancellation.window-hours must be at least 1");
            }
        }
    }

    /** A labelled price range; a {@code null} bound is open-ended. Both bounds are inclusive. */
    public record PriceBand(String label, BigDecimal minPrice, BigDecimal maxPrice) {
        public PriceBand {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("A price band needs a label");
            }
            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                throw new IllegalArgumentException("Price band '" + label + "' has min-price above max-price");
            }
        }
    }
}
