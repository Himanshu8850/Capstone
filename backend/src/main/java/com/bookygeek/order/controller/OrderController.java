package com.bookygeek.order.controller;

import com.bookygeek.cart.dto.MergeResult;
import com.bookygeek.common.security.AuthenticatedCustomer;
import com.bookygeek.order.dto.OrderResponse;
import com.bookygeek.order.dto.OrderSummaryResponse;
import com.bookygeek.order.service.BuyAgainService;
import com.bookygeek.order.service.OrderService;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * {@code listOrders}, {@code getOrder}, {@code cancelOrder} and {@code buyAgain} exactly as in
 * {@code api/openapi.yaml} ({@code placeOrder} is in {@link CheckoutController}). Every operation works on the
 * signed-in customer's own orders only; an unknown order and another customer's order are both answered as 404.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final BuyAgainService buyAgainService;

    public OrderController(OrderService orderService, BuyAgainService buyAgainService) {
        this.orderService = orderService;
        this.buyAgainService = buyAgainService;
    }

    @GetMapping
    public List<OrderSummaryResponse> listOrders(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return orderService.listOrders(customer.id());
    }

    @GetMapping("/{id:\\d{1,18}}")
    public OrderResponse getOrder(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable @Min(value = 1, message = "Choose a valid order.") long id) {
        return orderService.getOrder(customer.id(), id);
    }

    @PostMapping("/{id:\\d{1,18}}/cancellation")
    public OrderResponse cancelOrder(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable @Min(value = 1, message = "Choose a valid order.") long id) {
        return orderService.cancelOrder(customer.id(), id);
    }

    @PostMapping("/{id:\\d{1,18}}/buy-again")
    public MergeResult buyAgain(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable @Min(value = 1, message = "Choose a valid order.") long id) {
        return buyAgainService.buyAgain(customer.id(), id);
    }
}
