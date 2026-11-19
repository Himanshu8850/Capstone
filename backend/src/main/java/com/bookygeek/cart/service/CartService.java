package com.bookygeek.cart.service;

import com.bookygeek.cart.dto.BasketAdjustment;
import com.bookygeek.cart.dto.BasketAdjustment.Reason;
import com.bookygeek.cart.dto.BasketLine;
import com.bookygeek.cart.dto.BasketLineRequest;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.dto.MergeReport;
import com.bookygeek.cart.dto.MergeResult;
import com.bookygeek.cart.dto.PricedLine;
import com.bookygeek.cart.dto.Totals;
import com.bookygeek.cart.entity.BasketItem;
import com.bookygeek.cart.repository.BasketItemRepository;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The basket: a customer's lines, a public price preview for guests and the merge of a guest basket at login.
 * A line holds 1 to 10 units and, for a physical book, never more than its stock; adding reserves no stock. The
 * Catalogue module is used through {@link CatalogueService} only and all money arithmetic is delegated to
 * {@link PricingService}. Lines whose book is no longer listed are not shown.
 */
@Service
@Transactional(readOnly = true)
public class CartService {

    /** Largest quantity of one book in a basket. */
    public static final int MAX_QUANTITY = 10;

    private final BasketItemRepository items;
    private final CatalogueService catalogue;
    private final PricingService pricing;
    private final Clock clock;

    public CartService(BasketItemRepository items, CatalogueService catalogue, PricingService pricing, Clock clock) {
        this.items = items;
        this.catalogue = catalogue;
        this.pricing = pricing;
        this.clock = clock;
    }

    /** The customer's basket, priced with the current catalogue prices. */
    public BasketResponse getBasket(long customerId) {
        List<BasketItem> lines = items.findByCustomerIdOrderByBookId(customerId);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        lines.forEach(line -> quantities.put(line.getBookId(), line.getQuantity()));
        return priced(quantities);
    }

    /**
     * Prices lines sent by a guest without storing anything. A book listed twice is summed (at most 10 units are
     * priced); unknown or unlisted books are left out; an out-of-stock book is priced and flagged by
     * {@code available = false}.
     */
    public BasketResponse preview(List<BasketLineRequest> requested) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (BasketLineRequest line : requested) {
            quantities.merge(line.bookId(), line.quantity(), Integer::sum);
        }
        quantities.replaceAll((id, quantity) -> Math.min(quantity, MAX_QUANTITY));
        return priced(quantities);
    }

    /** Adds {@code quantity} units; a book already in the basket has its quantity increased. */
    @Transactional
    public BasketResponse addItem(long customerId, long bookId, int quantity) {
        BookSummary book = listedBook(bookId);
        BasketItem existing = items.findByCustomerIdAndBookId(customerId, bookId).orElse(null);
        int wanted = (existing == null ? 0 : existing.getQuantity()) + quantity;
        checkQuantity(book, wanted);
        save(existing, customerId, bookId, wanted);
        return getBasket(customerId);
    }

    /** Sets the quantity of a line already in the basket. */
    @Transactional
    public BasketResponse updateItem(long customerId, long bookId, int quantity) {
        BasketItem existing = items.findByCustomerIdAndBookId(customerId, bookId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        BookSummary book = listedBook(bookId);
        checkQuantity(book, quantity);
        existing.changeQuantity(quantity, clock.instant());
        items.save(existing);
        return getBasket(customerId);
    }

    /** Removes a line; the result is an empty basket after the last removal. */
    @Transactional
    public BasketResponse removeItem(long customerId, long bookId) {
        BasketItem existing = items.findByCustomerIdAndBookId(customerId, bookId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        items.delete(existing);
        items.flush();
        return getBasket(customerId);
    }

    /**
     * Merges guest lines into the customer's basket. Quantities for the same book are summed and capped at
     * min(10, stock); an out-of-stock or no-longer-listed book is skipped. Every skip or reduction is reported.
     * Existing basket lines are never reduced by a merge.
     */
    @Transactional
    public MergeResult merge(long customerId, List<BasketLineRequest> guestLines) {
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (BasketLineRequest line : guestLines) {
            requested.merge(line.bookId(), line.quantity(), Integer::sum);
        }
        Map<Long, BookSummary> books = catalogue.listedSummaries(requested.keySet()).stream()
                .collect(Collectors.toMap(BookSummary::id, Function.identity()));
        Map<Long, BasketItem> existing = items.findByCustomerIdOrderByBookId(customerId).stream()
                .collect(Collectors.toMap(BasketItem::getBookId, Function.identity()));

        List<BasketAdjustment> adjustments = new ArrayList<>();
        requested.forEach((bookId, quantity) -> {
            BookSummary book = books.get(bookId);
            if (book == null) {
                adjustments.add(new BasketAdjustment(bookId, "Book " + bookId, Reason.NOT_IN_CATALOGUE, quantity, 0));
                return;
            }
            if (!book.available()) {
                adjustments.add(new BasketAdjustment(bookId, book.title(), Reason.UNAVAILABLE, quantity, 0));
                return;
            }
            BasketItem current = existing.get(bookId);
            int before = current == null ? 0 : current.getQuantity();
            int target = Math.max(before, Math.min(limitOf(book), before + quantity));
            if (target != before) {
                save(current, customerId, bookId, target);
            }
            if (before + quantity > target) {
                adjustments.add(new BasketAdjustment(bookId, book.title(), Reason.QUANTITY_REDUCED, quantity,
                        target - before));
            }
        });
        return new MergeResult(getBasket(customerId), new MergeReport(List.copyOf(adjustments)));
    }

    /** Removes every line of the customer's basket (used after an order is placed). */
    @Transactional
    public void clear(long customerId) {
        items.deleteAll(items.findByCustomerIdOrderByBookId(customerId));
        items.flush();
    }

    private void save(BasketItem existing, long customerId, long bookId, int quantity) {
        if (existing == null) {
            items.save(new BasketItem(customerId, bookId, quantity, clock.instant()));
        } else {
            existing.changeQuantity(quantity, clock.instant());
            items.save(existing);
        }
    }

    private BookSummary listedBook(long bookId) {
        List<BookSummary> found = catalogue.listedSummaries(List.of(bookId));
        if (found.isEmpty()) {
            throw new ApiException(ErrorCode.BOOK_NOT_FOUND);
        }
        return found.get(0);
    }

    /** The largest quantity of this book a basket may hold: 10, or less for a physical book with less stock. */
    private static int limitOf(BookSummary book) {
        return book.format() == BookFormat.EBOOK ? MAX_QUANTITY : Math.min(MAX_QUANTITY, stockOf(book));
    }

    private static int stockOf(BookSummary book) {
        return book.stockQuantity() == null ? 0 : book.stockQuantity();
    }

    private static void checkQuantity(BookSummary book, int quantity) {
        if (quantity > MAX_QUANTITY) {
            throw new ApiException(ErrorCode.QUANTITY_LIMIT);
        }
        if (quantity > limitOf(book)) {
            throw new ApiException(ErrorCode.OUT_OF_STOCK);
        }
    }

    private BasketResponse priced(Map<Long, Integer> quantities) {
        List<BookSummary> books = catalogue.listedSummaries(quantities.keySet());
        List<BasketLine> lines = new ArrayList<>();
        List<PricedLine> priced = new ArrayList<>();
        int units = 0;
        for (BookSummary book : books) {
            int quantity = quantities.get(book.id());
            PricedLine line = new PricedLine(new BigDecimal(book.price()), quantity, book.format() == BookFormat.EBOOK);
            lines.add(new BasketLine(book, quantity, PricingService.format(line.lineTotal())));
            priced.add(line);
            units += quantity;
        }
        Totals totals = pricing.price(priced);
        return new BasketResponse(List.copyOf(lines), units, PricingService.format(totals.subtotal()),
                PricingService.format(totals.tax()), PricingService.format(totals.deliveryCharge()),
                PricingService.format(totals.total()));
    }
}
