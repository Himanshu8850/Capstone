package com.bookygeek.order.service;

import com.bookygeek.cart.dto.BasketAdjustment;
import com.bookygeek.cart.dto.BasketLineRequest;
import com.bookygeek.cart.dto.MergeReport;
import com.bookygeek.cart.dto.MergeResult;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.order.entity.OrderItem;
import com.bookygeek.order.repository.OrderItemRepository;
import com.bookygeek.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buy Again (FR-ORD-03): puts the books of an earlier order back into the customer's basket at their <em>current</em>
 * price and delivery date, never the price paid. The rules are the basket's own (a line holds 1 to 10 units and a
 * physical book never more than its stock), so each line is added with {@link CartService#merge}: an out-of-stock or
 * no-longer-listed book is not added and is reported, and a quantity above the stock or the limit is reduced and
 * reported. An order of another customer is {@code NOT_FOUND}; the order itself is never changed.
 */
@Service
@Transactional(readOnly = true)
public class BuyAgainService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final CartService cart;
    private final CatalogueService catalogue;

    public BuyAgainService(OrderRepository orders, OrderItemRepository orderItems, CartService cart,
            CatalogueService catalogue) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.cart = cart;
        this.catalogue = catalogue;
    }

    @Transactional
    public MergeResult buyAgain(long customerId, long orderId) {
        orders.findByIdAndCustomerId(orderId, customerId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        List<OrderItem> lines = orderItems.findByOrderIdOrderByBookId(orderId);
        List<BasketLineRequest> requested = lines.stream()
                .map(line -> new BasketLineRequest(line.getBookId(), line.getQuantity())).toList();
        MergeResult result = cart.merge(customerId, requested);
        return new MergeResult(result.basket(), withRealTitles(result.report(), lines));
    }

    /** The basket only knows listed books; the order knows every book it contains, so a removed book keeps its title. */
    private MergeReport withRealTitles(MergeReport report, List<OrderItem> lines) {
        if (report.adjustments().stream().noneMatch(a -> a.reason() == BasketAdjustment.Reason.NOT_IN_CATALOGUE)) {
            return report;
        }
        Map<Long, String> titles = catalogue.summariesByIds(lines.stream().map(OrderItem::getBookId).toList())
                .stream().collect(Collectors.toMap(BookSummary::id, BookSummary::title, (a, b) -> a));
        return new MergeReport(report.adjustments().stream()
                .map(a -> a.reason() == BasketAdjustment.Reason.NOT_IN_CATALOGUE && titles.containsKey(a.bookId())
                        ? new BasketAdjustment(a.bookId(), titles.get(a.bookId()), a.reason(), a.requestedQuantity(),
                                a.addedQuantity())
                        : a)
                .toList());
    }
}
