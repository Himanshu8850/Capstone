package com.bookygeek.unit.order;

import com.bookygeek.cart.dto.BasketLine;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.cart.service.PricingService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.dto.PurchaseBook;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import com.bookygeek.order.dto.OrderResponse;
import com.bookygeek.order.dto.PlaceOrderRequest;
import com.bookygeek.order.dto.PlaceOrderResult;
import com.bookygeek.order.entity.OrderEntity;
import com.bookygeek.order.entity.OrderItem;
import com.bookygeek.order.repository.OrderItemRepository;
import com.bookygeek.order.repository.OrderRepository;
import com.bookygeek.order.service.OrderService;
import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentRecord;
import com.bookygeek.payment.dto.PaymentRequest;
import com.bookygeek.payment.dto.PaymentStatus;
import com.bookygeek.payment.service.PaymentService;
import com.bookygeek.reward.service.RewardService;
import com.bookygeek.user.dto.AddressRequest;
import com.bookygeek.user.dto.AddressResponse;
import com.bookygeek.user.service.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Order placement orchestration (T098): revalidation of the basket and the stock under the locks,
 * {@code BASKET_CHANGED} when {@code expectedPayableTotal} differs, {@code POINTS_LIMIT_EXCEEDED}, {@code EMPTY_BASKET},
 * a decline that leaves everything untouched, and a points deduction (which writes the {@code REDEEM} ledger row) only
 * when more than zero points are redeemed. Collaborating services are mocks; money arithmetic is the real
 * {@link PricingService}.
 */
class OrderServiceTest {

    private static final long CUSTOMER = 7L;
    private static final UUID KEY = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:15:30Z"), ZoneOffset.UTC);
    private static final PaymentRequest CARD = new PaymentRequest(
            new PaymentRequest.Card("4111-1111-1111-1111", "Asha Verma", "123", "12/2030"), null);

    private final BookygeekProperties properties = new BookygeekProperties(
            new BookygeekProperties.Rewards(1000, 50),
            new BookygeekProperties.Tax(new BigDecimal("0.05")),
            new BookygeekProperties.Delivery(new BigDecimal("40.00")),
            new BookygeekProperties.Cancellation(48),
            List.of(new BookygeekProperties.PriceBand("Any", null, null)));
    private final PricingService pricing = new PricingService(properties);

    private OrderRepository orders;
    private OrderItemRepository orderItems;
    private CartService cart;
    private RewardService rewards;
    private PaymentService payments;
    private CatalogueService catalogue;
    private AddressService addresses;
    private OrderService service;

    private BasketResponse basket = basketOf(line(7, BookFormat.PAPERBACK, "500.00", 2, 25));
    private List<PurchaseBook> lockedBooks = List.of(new PurchaseBook(7, "Title 7", BookFormat.PAPERBACK,
            new BigDecimal("500.00"), 25));
    private int balance = 1000;

    private static BookSummary summary(long id, BookFormat format, String price, Integer stock) {
        boolean ebook = format == BookFormat.EBOOK;
        return new BookSummary(id, "Title " + id, "Author", "Description", format, "Publisher", "English", price,
                "/covers/" + id + "-front.svg", List.of(), ebook || (stock != null && stock > 0),
                ebook ? null : stock, "2026-11-17");
    }

    private static BasketLine line(long id, BookFormat format, String price, int quantity, Integer stock) {
        BigDecimal total = new BigDecimal(price).multiply(BigDecimal.valueOf(quantity));
        return new BasketLine(summary(id, format, price, stock), quantity, PricingService.format(total));
    }

    private BasketResponse basketOf(BasketLine... lines) {
        if (lines.length == 0) {
            return new BasketResponse(List.of(), 0, "0.00", "0.00", "0.00", "0.00");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean physical = false;
        int units = 0;
        for (BasketLine l : lines) {
            subtotal = subtotal.add(new BigDecimal(l.lineTotal()));
            physical |= l.book().format() != BookFormat.EBOOK;
            units += l.quantity();
        }
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.05"));
        BigDecimal delivery = physical ? new BigDecimal("40.00") : BigDecimal.ZERO;
        return new BasketResponse(List.of(lines), units, PricingService.format(subtotal), PricingService.format(tax),
                PricingService.format(delivery), PricingService.format(subtotal.add(tax).add(delivery)));
    }

    private static PlaceOrderRequest request(PaymentMethod method, PaymentRequest payment, int points, String expected) {
        AddressRequest address = new AddressRequest("Asha Verma", "9876543210", "12 Lake View Road", null,
                "Bengaluru", "Karnataka", "560001", "India");
        return new PlaceOrderRequest(null, address, method, payment, points, expected);
    }

    @BeforeEach
    void setUp() throws Exception {
        orders = mock(OrderRepository.class);
        orderItems = mock(OrderItemRepository.class);
        cart = mock(CartService.class);
        rewards = mock(RewardService.class);
        payments = mock(PaymentService.class);
        catalogue = mock(CatalogueService.class);
        addresses = mock(AddressService.class);
        service = new OrderService(orders, orderItems, cart, pricing, rewards, payments, catalogue, addresses,
                properties, CLOCK);

        when(cart.getBasket(CUSTOMER)).thenAnswer(call -> basket);
        when(catalogue.lockForPurchase(anyCollection())).thenAnswer(call -> lockedBooks);
        when(rewards.lockAccount(CUSTOMER)).thenAnswer(call -> balance);
        when(payments.findByIdempotencyKey(eq(CUSTOMER), any())).thenReturn(Optional.empty());
        when(payments.authorise(eq(CUSTOMER), any(), any(), any(), any())).thenAnswer(call ->
                call.<PaymentMethod>getArgument(1) == PaymentMethod.COD
                        ? PaymentStatus.PENDING_ON_DELIVERY : PaymentStatus.PAID);
        when(addresses.validated(any())).thenAnswer(call -> call.getArgument(0));
        when(orders.saveAndFlush(any(OrderEntity.class))).thenAnswer(call -> {
            OrderEntity order = call.getArgument(0);
            Field id = OrderEntity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(order, 11L);
            return order;
        });
        when(orderItems.findByOrderIdOrderByBookId(11L)).thenAnswer(call -> List.of(
                new OrderItem(11L, 7L, 2, new BigDecimal("500.00"))));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(summary(7, BookFormat.PAPERBACK, "500.00", 23)));
        when(payments.findByOrderId(11L)).thenReturn(Optional.of(
                new PaymentRecord(11L, PaymentMethod.CREDIT_CARD, PaymentStatus.PAID)));
    }

    // ---- the happy path -------------------------------------------------------------------------------------------

    @Test
    void placesTheOrderInTheDocumentedStepsAndReturnsTheOrder() {
        PlaceOrderResult result = service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 100, "990.00"));

        OrderResponse order = result.order();
        assertThat(result.replayed()).isFalse();
        assertThat(order.id()).isEqualTo(11L);
        assertThat(order.reference()).isEqualTo("BG-000011");
        assertThat(order.subtotal()).isEqualTo("1000.00");
        assertThat(order.tax()).isEqualTo("50.00");
        assertThat(order.deliveryCharge()).isEqualTo("40.00");
        assertThat(order.pointsRedeemed()).isEqualTo(100);
        assertThat(order.payableTotal()).isEqualTo("990.00");
        assertThat(order.cancellable()).isTrue();
        assertThat(order.cancellableUntil()).isEqualTo(Instant.parse("2026-11-14T10:15:30Z"));
        assertThat(order.address().id()).isNull();

        InOrder steps = inOrder(catalogue, rewards, payments, orders, orderItems, cart);
        steps.verify(catalogue).lockForPurchase(anyCollection());
        steps.verify(rewards).lockAccount(CUSTOMER);
        steps.verify(payments).authorise(eq(CUSTOMER), eq(PaymentMethod.CREDIT_CARD), eq(CARD),
                eq(new BigDecimal("990.00")), eq(KEY));
        steps.verify(orders).saveAndFlush(any(OrderEntity.class));
        steps.verify(orderItems).saveAllAndFlush(any());
        steps.verify(payments).record(CUSTOMER, 11L, PaymentMethod.CREDIT_CARD, PaymentStatus.PAID,
                new BigDecimal("990.00"), KEY);
        steps.verify(catalogue).decrementStock(Map.of(7L, 2));
        steps.verify(rewards).deduct(CUSTOMER, 11L, 100);
        steps.verify(cart).clear(CUSTOMER);
    }

    @Test
    void theOrderSnapshotsTheBookPriceAndTheAddress() {
        service.placeOrder(CUSTOMER, KEY, request(PaymentMethod.CREDIT_CARD, CARD, 0, "1090.00"));

        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orders).saveAndFlush(saved.capture());
        assertThat(saved.getValue().shipping().city()).isEqualTo("Bengaluru");
        assertThat(saved.getValue().getPayableTotal()).isEqualByComparingTo("1090.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderItem>> items = ArgumentCaptor.forClass(List.class);
        verify(orderItems).saveAllAndFlush(items.capture());
        assertThat(items.getValue()).singleElement().satisfies(item -> {
            assertThat(item.getBookId()).isEqualTo(7L);
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getUnitPrice()).isEqualByComparingTo("500.00");
        });
    }

    // ---- points: deduction (and with it the REDEEM row) only when more than zero are redeemed ------------------------

    @Test
    void noPointsMeansNoDeduction() {
        service.placeOrder(CUSTOMER, KEY, request(PaymentMethod.CREDIT_CARD, CARD, 0, "1090.00"));

        verify(rewards, never()).deduct(anyLong(), anyLong(), anyInt());
    }

    @Test
    void redeemedPointsAreDeductedOnceForTheOrder() {
        service.placeOrder(CUSTOMER, KEY, request(PaymentMethod.CREDIT_CARD, CARD, 545, "545.00"));

        verify(rewards).deduct(CUSTOMER, 11L, 545);
    }

    @Test
    void oneMorePointThanTheCapIsRejectedBeforePayment() {
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 546, "544.00")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.POINTS_LIMIT_EXCEEDED);
                    assertThat(e.getMessage()).contains("545");
                });
        verifyNothingWasChanged();
    }

    @Test
    void pointsAboveTheBalanceAreRejected() {
        balance = 100;
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 101, "989.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.POINTS_LIMIT_EXCEEDED));
        verifyNothingWasChanged();
    }

    // ---- revalidation ---------------------------------------------------------------------------------------------

    @Test
    void anEmptyBasketIsRejected() {
        basket = basketOf();
        lockedBooks = List.of();
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY, request(PaymentMethod.COD, null, 0, "0.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMPTY_BASKET));
        verifyNothingWasChanged();
    }

    @Test
    void aDifferentExpectedPayableTotalIsBasketChanged() {
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 0, "1000.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BASKET_CHANGED));
        verifyNothingWasChanged();
    }

    @Test
    void aPriceChangedUnderTheLockIsBasketChanged() {
        lockedBooks = List.of(new PurchaseBook(7, "Title 7", BookFormat.PAPERBACK, new BigDecimal("550.00"), 25));
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 0, "1090.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BASKET_CHANGED));
        verifyNothingWasChanged();
    }

    @Test
    void stockBelowTheBasketQuantityUnderTheLockIsOutOfStock() {
        lockedBooks = List.of(new PurchaseBook(7, "Title 7", BookFormat.PAPERBACK, new BigDecimal("500.00"), 1));
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 0, "1090.00")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
                    assertThat(e.getMessage()).contains("Only 1 copy");
                });
        verifyNothingWasChanged();
    }

    @Test
    void aBookThatLeftTheCatalogueIsBasketChanged() {
        lockedBooks = List.of();
        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 0, "1090.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.BASKET_CHANGED));
        verifyNothingWasChanged();
    }

    // ---- eBooks and COD -------------------------------------------------------------------------------------------

    @Test
    void anEbookOnlyOrderHasNoDeliveryAndNeverTouchesStock() {
        basket = basketOf(line(13, BookFormat.EBOOK, "99.00", 1, null));
        lockedBooks = List.of(new PurchaseBook(13, "Title 13", BookFormat.EBOOK, new BigDecimal("99.00"), null));
        when(orderItems.findByOrderIdOrderByBookId(11L)).thenReturn(List.of(
                new OrderItem(11L, 13L, 1, new BigDecimal("99.00"))));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(summary(13, BookFormat.EBOOK, "99.00", null)));
        when(payments.findByOrderId(11L)).thenReturn(Optional.of(
                new PaymentRecord(11L, PaymentMethod.COD, PaymentStatus.PENDING_ON_DELIVERY)));

        PlaceOrderResult result = service.placeOrder(CUSTOMER, KEY, request(PaymentMethod.COD, null, 0, "103.95"));

        assertThat(result.order().deliveryCharge()).isEqualTo("0.00");
        assertThat(result.order().paymentStatus()).isEqualTo(PaymentStatus.PENDING_ON_DELIVERY);
        verify(catalogue).decrementStock(Map.of());
    }

    @Test
    void codIsPlacedWithPendingPaymentAndNoDetails() {
        service.placeOrder(CUSTOMER, KEY, request(PaymentMethod.COD, null, 0, "1090.00"));

        verify(payments).record(CUSTOMER, 11L, PaymentMethod.COD, PaymentStatus.PENDING_ON_DELIVERY,
                new BigDecimal("1090.00"), KEY);
    }

    // ---- a declined payment leaves everything unchanged --------------------------------------------------------------

    @Test
    void aDeclinedPaymentCreatesNoOrderAndChangesNothing() {
        when(payments.authorise(eq(CUSTOMER), any(), any(), any(), any()))
                .thenThrow(new ApiException(ErrorCode.PAYMENT_DECLINED));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 100, "990.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED));

        verify(orders, never()).saveAndFlush(any());
        verify(orderItems, never()).saveAllAndFlush(any());
        verify(payments, never()).record(anyLong(), anyLong(), any(), any(), any(), any());
        verify(catalogue, never()).decrementStock(any());
        verify(rewards, never()).deduct(anyLong(), anyLong(), anyInt());
        verify(cart, never()).clear(anyLong());
    }

    // ---- address and payment shape ---------------------------------------------------------------------------------

    @Test
    void exactlyOneAddressSourceIsRequired() {
        AddressRequest address = new AddressRequest("Asha Verma", "9876543210", "12 Lake View Road", null,
                "Bengaluru", "Karnataka", "560001", "India");
        PlaceOrderRequest neither = new PlaceOrderRequest(null, null, PaymentMethod.COD, null, 0, "1090.00");
        PlaceOrderRequest both = new PlaceOrderRequest(3L, address, PaymentMethod.COD, null, 0, "1090.00");

        for (PlaceOrderRequest invalid : List.of(neither, both)) {
            assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY, invalid))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        verifyNoInteractions(catalogue, rewards);
    }

    @Test
    void anEbookOnlyOrderNeedsNoAddressAndStoresNone() {
        basket = basketOf(line(9, BookFormat.EBOOK, "300.00", 1, null));
        lockedBooks = List.of(new PurchaseBook(9, "Title 9", BookFormat.EBOOK, new BigDecimal("300.00"), null));
        when(orderItems.findByOrderIdOrderByBookId(11L)).thenAnswer(call -> List.of(
                new OrderItem(11L, 9L, 1, new BigDecimal("300.00"))));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(summary(9, BookFormat.EBOOK, "300.00", null)));

        PlaceOrderResult result = service.placeOrder(CUSTOMER, KEY, new PlaceOrderRequest(null, null,
                PaymentMethod.CREDIT_CARD, CARD, 0, "315.00"));

        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orders).saveAndFlush(saved.capture());
        assertThat(saved.getValue().shipping()).isNull();
        assertThat(result.order().address()).isNull();
        verify(addresses, never()).findOwned(anyLong(), anyLong());
        verify(addresses, never()).validated(any());
    }

    @Test
    void aSavedAddressIsCopiedIntoTheOrder() {
        when(addresses.findOwned(CUSTOMER, 3L)).thenReturn(Optional.of(new AddressResponse(3L, "Meera Iyer",
                "9123456780", "5 Marine Drive", null, "Mumbai", "Maharashtra", "400020", "India")));

        service.placeOrder(CUSTOMER, KEY, new PlaceOrderRequest(3L, null, PaymentMethod.CREDIT_CARD, CARD, 0,
                "1090.00"));

        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orders).saveAndFlush(saved.capture());
        assertThat(saved.getValue().shipping().fullName()).isEqualTo("Meera Iyer");
        assertThat(saved.getValue().shipping().city()).isEqualTo("Mumbai");
    }

    @Test
    void aSavedAddressOfSomeoneElseIsRejected() {
        when(addresses.findOwned(CUSTOMER, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY, new PlaceOrderRequest(3L, null,
                PaymentMethod.CREDIT_CARD, CARD, 0, "1090.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        verifyNothingWasChanged();
    }

    // ---- idempotency ---------------------------------------------------------------------------------------------

    @Test
    void theSameKeyReturnsTheStoredOrderWithoutPayingAgain() {
        when(payments.findByIdempotencyKey(CUSTOMER, KEY)).thenReturn(Optional.of(
                new PaymentRecord(11L, PaymentMethod.CREDIT_CARD, PaymentStatus.PAID)));
        OrderEntity stored = new OrderEntity(CUSTOMER, CLOCK.instant(), new OrderEntity.Shipping("Asha Verma",
                "9876543210", "12 Lake View Road", null, "Bengaluru", "Karnataka", "560001", "India"),
                new BigDecimal("1000.00"), new BigDecimal("50.00"), new BigDecimal("40.00"), 100,
                new BigDecimal("990.00"));
        setId(stored, 11L);
        when(orders.findByIdAndCustomerId(11L, CUSTOMER)).thenReturn(Optional.of(stored));

        PlaceOrderResult result = service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 100, "990.00"));

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(11L);
        verify(payments, never()).authorise(anyLong(), any(), any(), any(), any());
        verify(orders, never()).saveAndFlush(any());
        verify(catalogue, never()).decrementStock(any());
        verify(rewards, never()).deduct(anyLong(), anyLong(), anyInt());
    }

    @Test
    void aKeyThatWasDeclinedBeforeIsDeclinedAgainWithoutRunningThePayment() {
        when(payments.findByIdempotencyKey(CUSTOMER, KEY)).thenReturn(Optional.of(
                new PaymentRecord(null, PaymentMethod.CREDIT_CARD, PaymentStatus.FAILED)));

        assertThatThrownBy(() -> service.placeOrder(CUSTOMER, KEY,
                request(PaymentMethod.CREDIT_CARD, CARD, 0, "1090.00")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED));
        verify(payments, never()).authorise(anyLong(), any(), any(), any(), any());
    }

    // ---- getOrder ------------------------------------------------------------------------------------------------

    @Test
    void anotherCustomersOrderIsNotFound() {
        when(orders.findByIdAndCustomerId(11L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(99L, 11L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void aCancelledOrderIsNotCancellableAndHasNoDeadline() {
        OrderEntity cancelled = new OrderEntity(CUSTOMER, CLOCK.instant(), new OrderEntity.Shipping("Asha Verma",
                "9876543210", "12 Lake View Road", null, "Bengaluru", "Karnataka", "560001", "India"),
                new BigDecimal("1000.00"), new BigDecimal("50.00"), new BigDecimal("40.00"), 0,
                new BigDecimal("1090.00"));
        setId(cancelled, 11L);
        setField(cancelled, "status", com.bookygeek.order.dto.OrderStatus.CANCELLED);
        when(orders.findByIdAndCustomerId(11L, CUSTOMER)).thenReturn(Optional.of(cancelled));

        OrderResponse order = service.getOrder(CUSTOMER, 11L);

        assertThat(order.cancellable()).isFalse();
        assertThat(order.cancellableUntil()).isNull();
    }

    @Test
    void anOrderExactlyAtTheWindowIsNotCancellable() {
        OrderEntity old = new OrderEntity(CUSTOMER, CLOCK.instant().minusSeconds(48 * 3600), new OrderEntity.Shipping(
                "Asha Verma", "9876543210", "12 Lake View Road", null, "Bengaluru", "Karnataka", "560001", "India"),
                new BigDecimal("1000.00"), new BigDecimal("50.00"), new BigDecimal("40.00"), 0,
                new BigDecimal("1090.00"));
        setId(old, 11L);
        when(orders.findByIdAndCustomerId(11L, CUSTOMER)).thenReturn(Optional.of(old));

        assertThat(service.getOrder(CUSTOMER, 11L).cancellable()).isFalse();
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    /** The failed attempt must have written nothing: no order, no stock change, no points, basket kept. */
    private void verifyNothingWasChanged() {
        verify(orders, never()).saveAndFlush(any());
        verify(orderItems, never()).saveAllAndFlush(any());
        verify(payments, never()).authorise(anyLong(), any(), any(), any(), any());
        verify(payments, never()).record(anyLong(), anyLong(), any(), any(), any(), any());
        verify(catalogue, never()).decrementStock(any());
        verify(rewards, never()).deduct(anyLong(), anyLong(), anyInt());
        verify(cart, never()).clear(anyLong());
    }

    private static void setId(OrderEntity order, long id) {
        setField(order, "id", id);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
