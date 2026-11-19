package com.bookygeek.unit.order;

import com.bookygeek.cart.dto.BasketAdjustment;
import com.bookygeek.cart.dto.BasketLineRequest;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.dto.MergeReport;
import com.bookygeek.cart.dto.MergeResult;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.order.entity.OrderEntity;
import com.bookygeek.order.entity.OrderItem;
import com.bookygeek.order.repository.OrderItemRepository;
import com.bookygeek.order.repository.OrderRepository;
import com.bookygeek.order.service.BuyAgainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Buy Again (T116, FR-ORD-03): every line of the earlier order goes to the basket service as one merge, so the basket's
 * own rules (current price, 1 to 10 units, never more than the stock) decide what is added; the report it returns is
 * passed on, with the real title of a book that is no longer listed. Another customer's order is {@code NOT_FOUND}.
 */
class BuyAgainServiceTest {

    private static final long CUSTOMER = 7L;
    private static final long ORDER = 11L;

    private OrderRepository orders;
    private OrderItemRepository orderItems;
    private CartService cart;
    private CatalogueService catalogue;
    private BuyAgainService service;

    private static BookSummary summary(long id, String title) {
        return new BookSummary(id, title, "Author", "Description", BookFormat.PAPERBACK, "Publisher", "English",
                "500.00", "/covers/" + id + "-front.svg", List.of(), true, 5, "2026-11-17");
    }

    private static BasketResponse emptyBasket() {
        return new BasketResponse(List.of(), 0, "0.00", "0.00", "0.00", "0.00");
    }

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        orderItems = mock(OrderItemRepository.class);
        cart = mock(CartService.class);
        catalogue = mock(CatalogueService.class);
        service = new BuyAgainService(orders, orderItems, cart, catalogue);

        when(orders.findByIdAndCustomerId(ORDER, CUSTOMER)).thenReturn(Optional.of(mock(OrderEntity.class)));
        when(orderItems.findByOrderIdOrderByBookId(ORDER)).thenReturn(List.of(
                new OrderItem(ORDER, 7L, 2, new BigDecimal("450.00")),
                new OrderItem(ORDER, 12L, 1, new BigDecimal("99.00"))));
    }

    @Test
    void everyLineOfTheOrderIsMergedIntoTheBasketWithItsOrderedQuantity() {
        when(cart.merge(anyLong(), anyList())).thenReturn(new MergeResult(emptyBasket(),
                new MergeReport(List.of())));

        service.buyAgain(CUSTOMER, ORDER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BasketLineRequest>> lines = ArgumentCaptor.forClass(List.class);
        verify(cart).merge(org.mockito.ArgumentMatchers.eq(CUSTOMER), lines.capture());
        assertThat(lines.getValue()).containsExactly(new BasketLineRequest(7L, 2), new BasketLineRequest(12L, 1));
    }

    @Test
    void theBasketAndAnEmptyReportComeBackWhenNothingNeededAdjusting() {
        BasketResponse basket = emptyBasket();
        MergeReport report = new MergeReport(List.of());
        when(cart.merge(anyLong(), anyList())).thenReturn(new MergeResult(basket, report));

        MergeResult result = service.buyAgain(CUSTOMER, ORDER);

        assertThat(result.basket()).isSameAs(basket);
        assertThat(result.report().adjustments()).isEmpty();
        verifyNoInteractions(catalogue);
    }

    @Test
    void anOutOfStockBookAndAReducedQuantityAreReportedAsTheBasketReportsThem() {
        List<BasketAdjustment> adjustments = List.of(
                new BasketAdjustment(7L, "Title 7", BasketAdjustment.Reason.QUANTITY_REDUCED, 2, 1),
                new BasketAdjustment(12L, "Title 12", BasketAdjustment.Reason.UNAVAILABLE, 1, 0));
        when(cart.merge(anyLong(), anyList())).thenReturn(new MergeResult(emptyBasket(),
                new MergeReport(adjustments)));

        MergeResult result = service.buyAgain(CUSTOMER, ORDER);

        assertThat(result.report().adjustments()).containsExactlyElementsOf(adjustments);
        verifyNoInteractions(catalogue);
    }

    @Test
    void aBookThatIsNoLongerListedKeepsItsRealTitleInTheReport() {
        when(cart.merge(anyLong(), anyList())).thenReturn(new MergeResult(emptyBasket(), new MergeReport(List.of(
                new BasketAdjustment(12L, "Book 12", BasketAdjustment.Reason.NOT_IN_CATALOGUE, 1, 0),
                new BasketAdjustment(7L, "Title 7", BasketAdjustment.Reason.QUANTITY_REDUCED, 2, 1)))));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(summary(12L, "The Withdrawn Book")));

        MergeResult result = service.buyAgain(CUSTOMER, ORDER);

        assertThat(result.report().adjustments()).containsExactly(
                new BasketAdjustment(12L, "The Withdrawn Book", BasketAdjustment.Reason.NOT_IN_CATALOGUE, 1, 0),
                new BasketAdjustment(7L, "Title 7", BasketAdjustment.Reason.QUANTITY_REDUCED, 2, 1));
    }

    @Test
    void aBookWhoseTitleCannotBeFoundKeepsTheTitleTheBasketGave() {
        when(cart.merge(anyLong(), anyList())).thenReturn(new MergeResult(emptyBasket(), new MergeReport(List.of(
                new BasketAdjustment(12L, "Book 12", BasketAdjustment.Reason.NOT_IN_CATALOGUE, 1, 0)))));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of());

        MergeResult result = service.buyAgain(CUSTOMER, ORDER);

        assertThat(result.report().adjustments()).extracting(BasketAdjustment::title).containsExactly("Book 12");
    }

    @Test
    void anotherCustomersOrderIsNotFoundAndNothingIsAdded() {
        when(orders.findByIdAndCustomerId(ORDER, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buyAgain(99L, ORDER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verifyNoInteractions(cart);
    }

    @Test
    void theOrderItselfIsNeverChanged() {
        OrderEntity entity = mock(OrderEntity.class);
        when(orders.findByIdAndCustomerId(ORDER, CUSTOMER)).thenReturn(Optional.of(entity));
        when(cart.merge(anyLong(), anyList())).thenReturn(new MergeResult(emptyBasket(),
                new MergeReport(List.of())));

        service.buyAgain(CUSTOMER, ORDER);

        verify(orders, never()).save(org.mockito.ArgumentMatchers.any());
        verify(orders, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(entity, never()).cancel(org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
