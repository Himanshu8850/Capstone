package com.bookygeek.cart.service;

import com.bookygeek.cart.dto.PricedLine;
import com.bookygeek.cart.dto.Totals;
import com.bookygeek.common.props.BookygeekProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The single place where money is calculated: basket preview, basket, checkout quote and order placement all use it.
 * Rules (approved CL-07): tax is the configured rate of the subtotal, once per order, rounded HALF_UP to two decimals;
 * delivery is the configured charge per order when any physical book is present and zero for an eBook-only or empty
 * order; the reward cap is the configured percentage of subtotal + tax + delivery, rounded down to a whole point.
 */
@Service
public class PricingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BookygeekProperties properties;

    public PricingService(BookygeekProperties properties) {
        this.properties = properties;
    }

    /** Subtotal, tax, delivery and total of the given lines. An empty list prices to zero everywhere. */
    public Totals price(List<PricedLine> lines) {
        BigDecimal subtotal = money(lines.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal tax = money(subtotal.multiply(properties.tax().rate()));
        boolean anyPhysical = lines.stream().anyMatch(line -> !line.ebook());
        BigDecimal delivery = anyPhysical ? money(properties.delivery().charge()) : money(BigDecimal.ZERO);
        return new Totals(subtotal, tax, delivery, subtotal.add(tax).add(delivery));
    }

    /**
     * The most points that may be redeemed: {@code min(balance, floor(cap% x total))}, where {@code total} is
     * subtotal + tax + delivery before redemption. One point is worth one rupee.
     */
    public int maxRedeemablePoints(int balance, BigDecimal total) {
        BigDecimal cap = total.multiply(BigDecimal.valueOf(properties.rewards().capPercent()))
                .divide(HUNDRED, 0, RoundingMode.FLOOR);
        return cap.min(BigDecimal.valueOf(Math.max(balance, 0))).max(BigDecimal.ZERO).intValueExact();
    }

    /** An amount as the contract's {@code Money} string, for example {@code "1090.00"}. */
    public static String format(BigDecimal amount) {
        return money(amount).toPlainString();
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
