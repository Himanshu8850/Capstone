package com.bookygeek.unit.cart;

import com.bookygeek.cart.dto.BasketAdjustment;
import com.bookygeek.cart.dto.BasketAdjustment.Reason;
import com.bookygeek.cart.dto.BasketLineRequest;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.dto.MergeResult;
import com.bookygeek.cart.entity.BasketItem;
import com.bookygeek.cart.repository.BasketItemRepository;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.cart.service.PricingService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Basket rules (T084): a line holds 1..10 units and, for a physical book, never more than its stock
 * ({@code QUANTITY_LIMIT} / {@code OUT_OF_STOCK}); an eBook has no stock limit; a merge sums per book, caps at
 * min(10, stock), skips unavailable books and reports every adjustment. The repository is an in-memory stand-in.
 */
class CartServiceTest {

    private static final long CUSTOMER = 7L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:15:30Z"), ZoneOffset.UTC);

    private final Map<Long, BookSummary> catalogueBooks = new TreeMap<>();
    private final Map<Long, BasketItem> stored = new TreeMap<>();
    private CartService cart;

    private static BookSummary book(long id, BookFormat format, Integer stock, String price) {
        boolean ebook = format == BookFormat.EBOOK;
        return new BookSummary(id, "Title " + id, "Author", "Description", format, "Publisher", "English", price,
                "/covers/" + id + "-front.svg", List.of(), ebook || (stock != null && stock > 0),
                ebook ? null : stock, "2026-11-17");
    }

    private static BasketLineRequest line(long bookId, int quantity) {
        return new BasketLineRequest(bookId, quantity);
    }

    @BeforeEach
    void setUp() {
        BasketItemRepository items = mock(BasketItemRepository.class);
        when(items.findByCustomerIdOrderByBookId(anyLong()))
                .thenAnswer(call -> stored.values().stream().sorted(Comparator.comparing(BasketItem::getBookId)).toList());
        when(items.findByCustomerIdAndBookId(anyLong(), anyLong()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<Long>getArgument(1))));
        when(items.save(any(BasketItem.class))).thenAnswer(call -> {
            BasketItem item = call.getArgument(0);
            stored.put(item.getBookId(), item);
            return item;
        });

        CatalogueService catalogue = mock(CatalogueService.class);
        when(catalogue.listedSummaries(anyCollection())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return catalogueBooks.values().stream().filter(b -> ids.contains(b.id())).toList();
        });

        catalogueBooks.put(1L, book(1, BookFormat.PAPERBACK, 25, "499"));
        catalogueBooks.put(2L, book(2, BookFormat.PAPERBACK, 3, "200"));
        catalogueBooks.put(3L, book(3, BookFormat.EBOOK, null, "99"));
        catalogueBooks.put(5L, book(5, BookFormat.PAPERBACK, 0, "300"));

        PricingService pricing = new PricingService(new BookygeekProperties(
                new BookygeekProperties.Rewards(1000, 50),
                new BookygeekProperties.Tax(new BigDecimal("0.05")),
                new BookygeekProperties.Delivery(new BigDecimal("40.00")),
                new BookygeekProperties.Cancellation(48),
                List.of(new BookygeekProperties.PriceBand("Any", null, null))));
        cart = new CartService(items, catalogue, pricing, CLOCK);
    }

    private static ErrorCode errorOf(Runnable action) {
        try {
            action.run();
        } catch (ApiException e) {
            return e.getCode();
        }
        throw new AssertionError("Expected an ApiException");
    }

    // ---- add / update / remove ------------------------------------------------------------------------------------

    @Test
    void addingABookCreatesALineAndPricesTheBasket() {
        BasketResponse basket = cart.addItem(CUSTOMER, 1, 2);

        assertThat(basket.items()).hasSize(1);
        assertThat(basket.items().get(0).quantity()).isEqualTo(2);
        assertThat(basket.items().get(0).lineTotal()).isEqualTo("998.00");
        assertThat(basket.itemCount()).isEqualTo(2);
        assertThat(basket.subtotal()).isEqualTo("998.00");
        assertThat(basket.tax()).isEqualTo("49.90");
        assertThat(basket.deliveryCharge()).isEqualTo("40.00");
        assertThat(basket.total()).isEqualTo("1087.90");
    }

    @Test
    void addingAnExistingBookIncreasesItsQuantity() {
        cart.addItem(CUSTOMER, 1, 2);
        BasketResponse basket = cart.addItem(CUSTOMER, 1, 3);

        assertThat(basket.items()).hasSize(1);
        assertThat(basket.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    void anEleventhUnitIsRejectedWithQuantityLimitAndNothingChanges() {
        cart.addItem(CUSTOMER, 1, 10);

        assertThat(errorOf(() -> cart.addItem(CUSTOMER, 1, 1))).isEqualTo(ErrorCode.QUANTITY_LIMIT);
        assertThat(cart.getBasket(CUSTOMER).items().get(0).quantity()).isEqualTo(10);
    }

    @Test
    void aPhysicalBookNeverExceedsItsStock() {
        cart.addItem(CUSTOMER, 2, 3);

        assertThat(errorOf(() -> cart.addItem(CUSTOMER, 2, 1))).isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertThat(errorOf(() -> cart.addItem(CUSTOMER, 5, 1))).isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertThat(cart.getBasket(CUSTOMER).items()).hasSize(1);
    }

    @Test
    void anEBookHasNoStockLimitOnlyTheTenUnitLimit() {
        assertThat(cart.addItem(CUSTOMER, 3, 10).items().get(0).quantity()).isEqualTo(10);
        assertThat(errorOf(() -> cart.addItem(CUSTOMER, 3, 1))).isEqualTo(ErrorCode.QUANTITY_LIMIT);
    }

    @Test
    void anUnknownOrUnlistedBookIsBookNotFound() {
        assertThat(errorOf(() -> cart.addItem(CUSTOMER, 999, 1))).isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    void updatingSetsTheQuantityAndChecksTheSameLimits() {
        cart.addItem(CUSTOMER, 2, 1);

        assertThat(cart.updateItem(CUSTOMER, 2, 3).items().get(0).quantity()).isEqualTo(3);
        assertThat(errorOf(() -> cart.updateItem(CUSTOMER, 2, 4))).isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertThat(errorOf(() -> cart.updateItem(CUSTOMER, 2, 11))).isEqualTo(ErrorCode.QUANTITY_LIMIT);
        assertThat(cart.getBasket(CUSTOMER).items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void updatingOrRemovingABookNotInTheBasketIsNotFound() {
        assertThat(errorOf(() -> cart.updateItem(CUSTOMER, 1, 2))).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(errorOf(() -> cart.removeItem(CUSTOMER, 1))).isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---- preview --------------------------------------------------------------------------------------------------

    @Test
    void previewPricesGuestLinesAndSumsDuplicatesCappedAtTen() {
        BasketResponse basket = cart.preview(List.of(line(3, 8), line(3, 8), line(1, 1)));

        assertThat(basket.items()).extracting(l -> l.book().id()).containsExactly(1L, 3L);
        assertThat(basket.items().get(1).quantity()).isEqualTo(10);
        assertThat(basket.itemCount()).isEqualTo(11);
    }

    @Test
    void previewLeavesOutUnknownBooksAndFlagsOutOfStockOnes() {
        BasketResponse basket = cart.preview(List.of(line(5, 1), line(999, 1)));

        assertThat(basket.items()).hasSize(1);
        assertThat(basket.items().get(0).book().available()).isFalse();
    }

    // ---- merge ----------------------------------------------------------------------------------------------------

    @Test
    void mergeSumsPerBookWithTheCustomersExistingLines() {
        cart.addItem(CUSTOMER, 1, 2);
        MergeResult result = cart.merge(CUSTOMER, List.of(line(1, 3), line(3, 1)));

        assertThat(result.report().adjustments()).isEmpty();
        assertThat(result.basket().items()).extracting(l -> l.book().id(), l -> l.quantity())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 5), org.assertj.core.groups.Tuple.tuple(3L, 1));
    }

    @Test
    void mergeCapsAtTheLowerOfTenAndStockAndReportsIt() {
        MergeResult result = cart.merge(CUSTOMER, List.of(line(2, 5), line(1, 10), line(1, 10)));

        assertThat(result.basket().items()).extracting(l -> l.book().id(), l -> l.quantity())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 10), org.assertj.core.groups.Tuple.tuple(2L, 3));
        assertThat(result.report().adjustments()).containsExactlyInAnyOrder(
                new BasketAdjustment(2, "Title 2", Reason.QUANTITY_REDUCED, 5, 3),
                new BasketAdjustment(1, "Title 1", Reason.QUANTITY_REDUCED, 20, 10));
    }

    @Test
    void mergeSkipsOutOfStockAndUnlistedBooksAndReportsThem() {
        MergeResult result = cart.merge(CUSTOMER, List.of(line(5, 2), line(999, 1), line(3, 1)));

        assertThat(result.basket().items()).extracting(l -> l.book().id()).containsExactly(3L);
        assertThat(result.report().adjustments()).containsExactlyInAnyOrder(
                new BasketAdjustment(5, "Title 5", Reason.UNAVAILABLE, 2, 0),
                new BasketAdjustment(999, "Book 999", Reason.NOT_IN_CATALOGUE, 1, 0));
    }

    @Test
    void mergeNeverReducesALineTheCustomerAlreadyHas() {
        cart.addItem(CUSTOMER, 2, 3);
        MergeResult result = cart.merge(CUSTOMER, List.of(line(2, 2)));

        assertThat(result.basket().items().get(0).quantity()).isEqualTo(3);
        assertThat(result.report().adjustments()).containsExactly(
                new BasketAdjustment(2, "Title 2", Reason.QUANTITY_REDUCED, 2, 0));
    }
}
