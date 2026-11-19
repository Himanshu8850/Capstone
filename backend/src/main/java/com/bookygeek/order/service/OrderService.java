package com.bookygeek.order.service;

import com.bookygeek.cart.dto.BasketLine;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.dto.PricedLine;
import com.bookygeek.cart.dto.Totals;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.cart.service.PricingService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.dto.PurchaseBook;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.common.error.ApiError;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import com.bookygeek.order.dto.OrderItemResponse;
import com.bookygeek.order.dto.OrderResponse;
import com.bookygeek.order.dto.OrderStatus;
import com.bookygeek.order.dto.OrderSummaryResponse;
import com.bookygeek.order.dto.PlaceOrderRequest;
import com.bookygeek.order.dto.PlaceOrderResult;
import com.bookygeek.order.entity.OrderEntity;
import com.bookygeek.order.entity.OrderItem;
import com.bookygeek.order.repository.OrderItemRepository;
import com.bookygeek.order.repository.OrderRepository;
import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentRecord;
import com.bookygeek.payment.dto.PaymentStatus;
import com.bookygeek.payment.service.PaymentService;
import com.bookygeek.reward.service.RewardService;
import com.bookygeek.user.dto.AddressRequest;
import com.bookygeek.user.dto.AddressResponse;
import com.bookygeek.user.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Order placement and order lookup. {@link #placeOrder} is one database transaction that either does everything or
 * nothing (FR-ORD-08, FR-PAY-10, FR-RWD-04):
 * <ol>
 *   <li>lock the ordered physical books ({@code SELECT … FOR UPDATE}, ascending id, so concurrent orders cannot
 *       deadlock) and then the reward account;</li>
 *   <li>replay an earlier result when the same {@code Idempotency-Key} was already used;</li>
 *   <li>revalidate the basket, the stock under the locks, {@code expectedPayableTotal} and the points cap;</li>
 *   <li>run the simulated payment: a decline writes a {@code FAILED} attempt in its own transaction and rolls the rest
 *       back, so basket, stock and points stay untouched;</li>
 *   <li>insert order, items and payment, take the stock, deduct the points (only when more than zero are redeemed)
 *       and clear the basket.</li>
 * </ol>
 * Other modules are used through their services only; money arithmetic is {@link PricingService}'s.
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartService cart;
    private final PricingService pricing;
    private final RewardService rewards;
    private final PaymentService payments;
    private final CatalogueService catalogue;
    private final AddressService addresses;
    private final BookygeekProperties properties;
    private final Clock clock;

    public OrderService(OrderRepository orders, OrderItemRepository orderItems, CartService cart,
            PricingService pricing, RewardService rewards, PaymentService payments, CatalogueService catalogue,
            AddressService addresses, BookygeekProperties properties, Clock clock) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.cart = cart;
        this.pricing = pricing;
        this.rewards = rewards;
        this.payments = payments;
        this.catalogue = catalogue;
        this.addresses = addresses;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public PlaceOrderResult placeOrder(long customerId, UUID idempotencyKey, PlaceOrderRequest request) {
        checkShape(customerId, request);
        PaymentMethod method = request.paymentMethod();
        int points = request.pointsToRedeem();

        // The basket is read once to know which books to lock and again under the locks, so a basket that a
        // concurrent request of the same customer has just emptied is never ordered twice.
        Map<Long, PurchaseBook> locked = catalogue.lockForPurchase(quantitiesOf(cart.getBasket(customerId)).keySet())
                .stream().collect(Collectors.toMap(PurchaseBook::id, Function.identity()));
        int balance = rewards.lockAccount(customerId);

        Optional<PaymentRecord> earlier = payments.findByIdempotencyKey(customerId, idempotencyKey);
        if (earlier.isPresent()) {
            return replay(customerId, earlier.get());
        }
        Map<Long, Integer> quantities = quantitiesOf(cart.getBasket(customerId));
        if (quantities.isEmpty()) {
            throw new ApiException(ErrorCode.EMPTY_BASKET);
        }

        List<PricedLine> priced = new ArrayList<>();
        Map<Long, Integer> physical = new TreeMap<>();
        quantities.forEach((bookId, quantity) -> {
            PurchaseBook book = locked.get(bookId);
            if (book == null) {
                throw new ApiException(ErrorCode.BASKET_CHANGED);
            }
            if (!book.ebook()) {
                int stock = book.stock() == null ? 0 : book.stock();
                if (stock < quantity) {
                    throw new ApiException(ErrorCode.OUT_OF_STOCK, outOfStockMessage(book.title(), stock));
                }
                physical.put(bookId, quantity);
            }
            priced.add(new PricedLine(book.price(), quantity, book.ebook()));
        });

        Totals totals = pricing.price(priced);
        BigDecimal payable = totals.total().subtract(BigDecimal.valueOf(points));
        if (new BigDecimal(request.expectedPayableTotal()).compareTo(payable) != 0) {
            throw new ApiException(ErrorCode.BASKET_CHANGED);
        }
        int max = pricing.maxRedeemablePoints(balance, totals.total());
        if (points > max) {
            throw new ApiException(ErrorCode.POINTS_LIMIT_EXCEEDED, CheckoutQuoteService.pointsLimitMessage(max));
        }

        // An order of e-books only has nothing to deliver, so it keeps no address (AMD-004).
        OrderEntity.Shipping shipping = physical.isEmpty() ? null : shippingOf(customerId, request);
        PaymentStatus paymentStatus = payments.authorise(customerId, method, request.payment(), payable, idempotencyKey);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        OrderEntity order = orders.saveAndFlush(new OrderEntity(customerId, now, shipping, totals.subtotal(),
                totals.tax(), totals.deliveryCharge(), points, payable));
        long orderId = order.getId();
        List<OrderItem> lines = new ArrayList<>();
        quantities.forEach((bookId, quantity) ->
                lines.add(new OrderItem(orderId, bookId, quantity, locked.get(bookId).price())));
        orderItems.saveAllAndFlush(lines);
        payments.record(customerId, orderId, method, paymentStatus, payable, idempotencyKey);

        catalogue.decrementStock(physical);
        if (points > 0) {
            rewards.deduct(customerId, orderId, points);
        }
        cart.clear(customerId);
        return new PlaceOrderResult(toResponse(order), false);
    }

    /** One of the signed-in customer's orders; another customer's order is {@code NOT_FOUND}. */
    public OrderResponse getOrder(long customerId, long orderId) {
        return toResponse(orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND)));
    }

    /** The customer's orders, newest first, each with its lines (FR-ORD-01). Empty when there are none. */
    public List<OrderSummaryResponse> listOrders(long customerId) {
        List<OrderEntity> mine = orders.findByCustomerIdOrderByPlacedAtDescIdDesc(customerId);
        if (mine.isEmpty()) {
            return List.of();
        }
        List<OrderItem> allLines = orderItems.findByOrderIdInOrderByOrderIdAscBookIdAsc(
                mine.stream().map(OrderEntity::getId).toList());
        Map<Long, BookSummary> books = catalogue.summariesByIds(
                allLines.stream().map(OrderItem::getBookId).distinct().toList())
                .stream().collect(Collectors.toMap(BookSummary::id, Function.identity()));
        Map<Long, List<OrderItem>> linesByOrder = allLines.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        return mine.stream().map(order -> new OrderSummaryResponse(order.getId(), order.getReference(),
                order.getStatus(), order.getPlacedAt(),
                linesByOrder.getOrDefault(order.getId(), List.of()).stream()
                        .map(line -> itemOf(line, books)).toList(),
                PricingService.format(order.getPayableTotal()))).toList();
    }

    /**
     * Cancels an order (FR-ORD-04..06) as one transaction. The order row is locked first, so two cancellations of the
     * same order are serialised and the second sees {@code ALREADY_CANCELLED}. The window is exclusive: an order is
     * cancellable only while it is strictly less than the configured 48 hours old, for every payment method. Then,
     * atomically: the status becomes {@code CANCELLED}, the ordered physical copies go back to stock (ascending book
     * id), the redeemed points are restored (a {@code RESTORE} ledger row only when more than zero were redeemed) and
     * the payment is settled ({@code PAID} to {@code REFUNDED}, COD to {@code CANCELLED}). Any failure rolls it all
     * back. Another customer's order is {@code NOT_FOUND}.
     */
    @Transactional
    public OrderResponse cancelOrder(long customerId, long orderId) {
        OrderEntity order = orders.findForUpdate(orderId, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new ApiException(ErrorCode.ALREADY_CANCELLED);
        }
        Instant now = clock.instant();
        Instant until = order.getPlacedAt().plus(properties.cancellation().windowHours(), ChronoUnit.HOURS);
        if (!now.isBefore(until)) {
            throw new ApiException(ErrorCode.CANCELLATION_WINDOW_EXPIRED);
        }

        List<OrderItem> lines = orderItems.findByOrderIdOrderByBookId(orderId);
        Map<Long, BookSummary> books = catalogue.summariesByIds(lines.stream().map(OrderItem::getBookId).toList())
                .stream().collect(Collectors.toMap(BookSummary::id, Function.identity()));
        Map<Long, Integer> physical = new TreeMap<>();
        lines.forEach(line -> {
            BookSummary book = books.get(line.getBookId());
            if (book != null && book.format() != BookFormat.EBOOK) {
                physical.put(line.getBookId(), line.getQuantity());
            }
        });

        order.cancel(now.truncatedTo(ChronoUnit.MICROS));
        orders.saveAndFlush(order);
        // The stock update clears the persistence context so the response below shows the restored stock.
        catalogue.incrementStock(physical);
        rewards.restore(customerId, orderId, order.getPointsRedeemed());
        payments.settleCancelledOrder(orderId);
        return toResponse(order);
    }

    private static Map<Long, Integer> quantitiesOf(BasketResponse basket) {
        Map<Long, Integer> quantities = new TreeMap<>();
        basket.items().forEach(line -> quantities.put(line.book().id(), line.quantity()));
        return quantities;
    }

    /**
     * One address source (none is accepted for a basket of e-books only), and payment details that fit the method;
     * nothing is locked before this passes.
     */
    private void checkShape(long customerId, PlaceOrderRequest request) {
        boolean neither = request.addressId() == null && request.address() == null;
        if ((request.addressId() != null && request.address() != null) || (neither && needsAddress(customerId))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, List.of(new ApiError.FieldError("addressId",
                    "Choose a saved address or enter a new one, not both.")));
        }
        payments.validateDetails(request.paymentMethod(), request.payment());
    }

    /** True unless the basket holds at least one book and every book is an e-book. */
    private boolean needsAddress(long customerId) {
        List<BasketLine> items = cart.getBasket(customerId).items();
        return items.isEmpty() || items.stream().anyMatch(line -> line.book().format() != BookFormat.EBOOK);
    }

    private OrderEntity.Shipping shippingOf(long customerId, PlaceOrderRequest request) {
        AddressRequest address;
        if (request.addressId() != null) {
            AddressResponse saved = addresses.findOwned(customerId, request.addressId())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, List.of(
                            new ApiError.FieldError("addressId", "Choose one of your saved addresses."))));
            address = new AddressRequest(saved.fullName(), saved.phone(), saved.addressLine1(), saved.addressLine2(),
                    saved.city(), saved.state(), saved.pinCode(), saved.country());
        } else {
            address = addresses.validated(request.address());
        }
        return new OrderEntity.Shipping(address.fullName(), address.phone(), address.addressLine1(),
                address.addressLine2(), address.city(), address.state(), address.pinCode(), address.country());
    }

    /** The stored result of an earlier request with the same key: the order, or the earlier decline. */
    private PlaceOrderResult replay(long customerId, PaymentRecord earlier) {
        if (earlier.orderId() == null) {
            throw new ApiException(ErrorCode.PAYMENT_DECLINED);
        }
        OrderEntity order = orders.findByIdAndCustomerId(earlier.orderId(), customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
        return new PlaceOrderResult(toResponse(order), true);
    }

    private OrderResponse toResponse(OrderEntity order) {
        List<OrderItem> lines = orderItems.findByOrderIdOrderByBookId(order.getId());
        Map<Long, BookSummary> books = catalogue.summariesByIds(lines.stream().map(OrderItem::getBookId).toList())
                .stream().collect(Collectors.toMap(BookSummary::id, Function.identity()));
        List<OrderItemResponse> items = lines.stream().map(line -> itemOf(line, books)).toList();
        PaymentRecord payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
        OrderEntity.Shipping ship = order.shipping();
        AddressResponse address = ship == null ? null : new AddressResponse(null, ship.fullName(), ship.phone(),
                ship.line1(), ship.line2(), ship.city(), ship.state(), ship.pinCode(), ship.country());
        Instant until = order.getStatus() == OrderStatus.PLACED
                ? order.getPlacedAt().plus(properties.cancellation().windowHours(), ChronoUnit.HOURS) : null;
        boolean cancellable = until != null && clock.instant().isBefore(until);
        return new OrderResponse(order.getId(), order.getReference(), order.getStatus(), order.getPlacedAt(), items,
                address, payment.method(), payment.status(), PricingService.format(order.getSubtotal()),
                PricingService.format(order.getTax()), PricingService.format(order.getDeliveryCharge()),
                order.getPointsRedeemed(), PricingService.format(order.getPayableTotal()), cancellable, until);
    }

    private static OrderItemResponse itemOf(OrderItem line, Map<Long, BookSummary> books) {
        return new OrderItemResponse(books.get(line.getBookId()), line.getQuantity(),
                PricingService.format(line.getUnitPrice()),
                PricingService.format(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()))));
    }

    private static String outOfStockMessage(String title, int stock) {
        return stock == 0 ? "\"" + title + "\" is out of stock."
                : "Only " + stock + (stock == 1 ? " copy" : " copies") + " of \"" + title + "\" "
                        + (stock == 1 ? "is" : "are") + " available.";
    }
}
