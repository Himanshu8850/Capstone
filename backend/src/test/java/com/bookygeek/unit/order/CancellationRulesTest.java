package com.bookygeek.unit.order;

import com.bookygeek.cart.service.CartService;
import com.bookygeek.cart.service.PricingService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import com.bookygeek.order.dto.OrderResponse;
import com.bookygeek.order.dto.OrderStatus;
import com.bookygeek.order.entity.OrderEntity;
import com.bookygeek.order.entity.OrderItem;
import com.bookygeek.order.repository.OrderItemRepository;
import com.bookygeek.order.repository.OrderRepository;
import com.bookygeek.order.service.OrderService;
import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentRecord;
import com.bookygeek.payment.dto.PaymentStatus;
import com.bookygeek.payment.service.PaymentService;
import com.bookygeek.reward.service.RewardService;
import com.bookygeek.user.service.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The cancellation rules of {@link OrderService#cancelOrder} (T115, FR-ORD-04..06) against a fixed clock: 47:59:59 is
 * allowed, exactly 48:00:00 is rejected (the window is exclusive), an already cancelled order and another customer's
 * order are rejected with no state change, stock goes back for physical books only, and the points and the payment
 * are settled as the clarifications say. Collaborating services are mocks.
 */
class CancellationRulesTest {

    private static final long CUSTOMER = 7L;
    private static final long ORDER = 11L;
    private static final Instant PLACED = Instant.parse("2026-11-12T10:15:30Z");

    private final BookygeekProperties properties = new BookygeekProperties(
            new BookygeekProperties.Rewards(1000, 50),
            new BookygeekProperties.Tax(new BigDecimal("0.05")),
            new BookygeekProperties.Delivery(new BigDecimal("40.00")),
            new BookygeekProperties.Cancellation(48),
            List.of(new BookygeekProperties.PriceBand("Any", null, null)));

    private OrderRepository orders;
    private OrderItemRepository orderItems;
    private RewardService rewards;
    private PaymentService payments;
    private CatalogueService catalogue;
    private OrderEntity order;

    private static BookSummary summary(long id, BookFormat format, Integer stock) {
        boolean ebook = format == BookFormat.EBOOK;
        return new BookSummary(id, "Title " + id, "Author", "Description", format, "Publisher", "English", "500.00",
                "/covers/" + id + "-front.svg", List.of(), ebook || (stock != null && stock > 0), ebook ? null : stock,
                "2026-11-17");
    }

    private static OrderEntity orderWith(int points) throws Exception {
        OrderEntity entity = new OrderEntity(CUSTOMER, PLACED,
                new OrderEntity.Shipping("Asha Verma", "9876543210", "12 Lake View Road", null, "Bengaluru",
                        "Karnataka", "560001", "India"),
                new BigDecimal("1000.00"), new BigDecimal("50.00"), new BigDecimal("40.00"), points,
                new BigDecimal("1090.00").subtract(BigDecimal.valueOf(points)));
        Field id = OrderEntity.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(entity, ORDER);
        return entity;
    }

    private OrderService serviceAt(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new OrderService(orders, orderItems, mock(CartService.class), new PricingService(properties), rewards,
                payments, catalogue, mock(AddressService.class), properties, clock);
    }

    @BeforeEach
    void setUp() throws Exception {
        orders = mock(OrderRepository.class);
        orderItems = mock(OrderItemRepository.class);
        rewards = mock(RewardService.class);
        payments = mock(PaymentService.class);
        catalogue = mock(CatalogueService.class);
        order = orderWith(100);

        when(orders.findForUpdate(ORDER, CUSTOMER)).thenAnswer(call -> Optional.of(order));
        when(orders.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(orders.findByIdAndCustomerId(ORDER, CUSTOMER)).thenAnswer(call -> Optional.of(order));
        when(orderItems.findByOrderIdOrderByBookId(ORDER)).thenReturn(List.of(
                new OrderItem(ORDER, 7L, 2, new BigDecimal("500.00")),
                new OrderItem(ORDER, 12L, 1, new BigDecimal("499.00"))));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(
                summary(7, BookFormat.PAPERBACK, 23), summary(12, BookFormat.EBOOK, null)));
        when(payments.settleCancelledOrder(ORDER)).thenReturn(PaymentStatus.REFUNDED);
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(
                new PaymentRecord(ORDER, PaymentMethod.CREDIT_CARD, PaymentStatus.REFUNDED)));
    }

    // ---- the window ------------------------------------------------------------------------------------------------

    @Test
    void anOrderThatIsFortySevenHoursFiftyNineMinutesFiftyNineSecondsOldCanBeCancelled() {
        OrderResponse cancelled = serviceAt(PLACED.plus(Duration.ofHours(48)).minusSeconds(1))
                .cancelOrder(CUSTOMER, ORDER);

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.cancellable()).isFalse();
        assertThat(cancelled.cancellableUntil()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isEqualTo(PLACED.plus(Duration.ofHours(48)).minusSeconds(1));
    }

    @Test
    void anOrderExactlyFortyEightHoursOldIsRejectedAndNothingChanges() {
        OrderService service = serviceAt(PLACED.plus(Duration.ofHours(48)));

        assertThatThrownBy(() -> service.cancelOrder(CUSTOMER, ORDER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CANCELLATION_WINDOW_EXPIRED));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(order.getCancelledAt()).isNull();
        verifyNothingWasChanged();
    }

    @Test
    void anOrderOlderThanFortyEightHoursIsRejected() {
        OrderService service = serviceAt(PLACED.plus(Duration.ofHours(48)).plusSeconds(1));

        assertThatThrownBy(() -> service.cancelOrder(CUSTOMER, ORDER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.CANCELLATION_WINDOW_EXPIRED));
        verifyNothingWasChanged();
    }

    @Test
    void theWindowIsTheSameForACashOnDeliveryOrder() {
        when(payments.settleCancelledOrder(ORDER)).thenReturn(PaymentStatus.CANCELLED);
        when(payments.findByOrderId(ORDER)).thenReturn(Optional.of(
                new PaymentRecord(ORDER, PaymentMethod.COD, PaymentStatus.CANCELLED)));

        assertThat(serviceAt(PLACED.plus(Duration.ofMinutes(47 * 60 + 59))).cancelOrder(CUSTOMER, ORDER).status())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void aFreshOrderIsReportedAsCancellableUntilFortyEightHoursAfterPlacement() {
        OrderResponse fresh = serviceAt(PLACED.plusSeconds(5)).getOrder(CUSTOMER, ORDER);

        assertThat(fresh.cancellable()).isTrue();
        assertThat(fresh.cancellableUntil()).isEqualTo(PLACED.plus(Duration.ofHours(48)));
    }

    @Test
    void theOrderIsNoLongerReportedAsCancellableAtExactlyFortyEightHours() {
        OrderResponse expired = serviceAt(PLACED.plus(Duration.ofHours(48))).getOrder(CUSTOMER, ORDER);

        assertThat(expired.cancellable()).isFalse();
    }

    // ---- who and what may be cancelled ----------------------------------------------------------------------------

    @Test
    void anAlreadyCancelledOrderIsRejectedWithAlreadyCancelled() {
        order.cancel(PLACED.plusSeconds(60));
        OrderService service = serviceAt(PLACED.plusSeconds(120));

        assertThatThrownBy(() -> service.cancelOrder(CUSTOMER, ORDER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ALREADY_CANCELLED));
        verifyNothingWasChanged();
    }

    @Test
    void anAlreadyCancelledOrderIsReportedAsCancelledEvenAfterTheWindowHasPassed() {
        order.cancel(PLACED.plusSeconds(60));

        assertThatThrownBy(() -> serviceAt(PLACED.plus(Duration.ofHours(100))).cancelOrder(CUSTOMER, ORDER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ALREADY_CANCELLED));
    }

    @Test
    void anotherCustomersOrderIsNotFoundAndNothingChanges() {
        when(orders.findForUpdate(ORDER, 99L)).thenReturn(Optional.empty());
        OrderService service = serviceAt(PLACED.plusSeconds(5));

        assertThatThrownBy(() -> service.cancelOrder(99L, ORDER))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PLACED);
        verifyNothingWasChanged();
    }

    @Test
    void anUnknownOrderIsNotFound() {
        when(orders.findForUpdate(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(PLACED.plusSeconds(5)).cancelOrder(CUSTOMER, 12345L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ---- what a cancellation does ---------------------------------------------------------------------------------

    @Test
    void aCancellationLocksTheOrderThenRestoresStockPointsAndPaymentInOneUnit() {
        serviceAt(PLACED.plusSeconds(5)).cancelOrder(CUSTOMER, ORDER);

        InOrder steps = inOrder(orders, catalogue, rewards, payments);
        steps.verify(orders).findForUpdate(ORDER, CUSTOMER);
        steps.verify(orders).saveAndFlush(order);
        steps.verify(catalogue).incrementStock(Map.of(7L, 2));
        steps.verify(rewards).restore(CUSTOMER, ORDER, 100);
        steps.verify(payments).settleCancelledOrder(ORDER);
    }

    @Test
    void onlyPhysicalBooksGoBackToStockAndAnEbookIsNeverCounted() {
        serviceAt(PLACED.plusSeconds(5)).cancelOrder(CUSTOMER, ORDER);

        verify(catalogue).incrementStock(Map.of(7L, 2));
    }

    @Test
    void anOrderWithOnlyAnEbookReturnsNoStock() {
        when(orderItems.findByOrderIdOrderByBookId(ORDER)).thenReturn(List.of(
                new OrderItem(ORDER, 12L, 1, new BigDecimal("499.00"))));

        serviceAt(PLACED.plusSeconds(5)).cancelOrder(CUSTOMER, ORDER);

        verify(catalogue).incrementStock(Map.of());
    }

    @Test
    void pointsAreRestoredWithTheNumberRedeemedOnTheOrder() throws Exception {
        order = orderWith(250);

        serviceAt(PLACED.plusSeconds(5)).cancelOrder(CUSTOMER, ORDER);

        verify(rewards).restore(CUSTOMER, ORDER, 250);
    }

    @Test
    void anOrderWithoutRedeemedPointsAsksForARestoreOfZeroWhichWritesNothing() throws Exception {
        order = orderWith(0);

        serviceAt(PLACED.plusSeconds(5)).cancelOrder(CUSTOMER, ORDER);

        // RewardService.restore treats zero as a no-op (no balance change, no RESTORE ledger row).
        verify(rewards).restore(CUSTOMER, ORDER, 0);
    }

    @Test
    void thePaymentIsSettledOnceThroughThePaymentService() {
        OrderResponse cancelled = serviceAt(PLACED.plusSeconds(5)).cancelOrder(CUSTOMER, ORDER);

        verify(payments).settleCancelledOrder(ORDER);
        assertThat(cancelled.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void aFailureWhileSettlingThePaymentPropagatesSoTheTransactionRollsBack() {
        when(payments.settleCancelledOrder(ORDER)).thenThrow(new ApiException(ErrorCode.INTERNAL_ERROR));
        OrderService service = serviceAt(PLACED.plusSeconds(5));

        assertThatThrownBy(() -> service.cancelOrder(CUSTOMER, ORDER))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    private void verifyNothingWasChanged() {
        verify(orders, never()).saveAndFlush(any());
        verifyNoInteractions(rewards);
        verify(payments, never()).settleCancelledOrder(anyLong());
        verify(catalogue, never()).incrementStock(any());
        verifyNoMoreInteractions(rewards);
    }
}
