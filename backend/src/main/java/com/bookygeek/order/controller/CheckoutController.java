package com.bookygeek.order.controller;

import com.bookygeek.common.security.AuthenticatedCustomer;
import com.bookygeek.order.dto.CheckoutQuote;
import com.bookygeek.order.dto.CheckoutQuoteRequest;
import com.bookygeek.order.dto.OrderResponse;
import com.bookygeek.order.dto.PlaceOrderRequest;
import com.bookygeek.order.dto.PlaceOrderResult;
import com.bookygeek.order.service.CheckoutQuoteService;
import com.bookygeek.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * {@code quoteCheckout} and {@code placeOrder} exactly as in {@code api/openapi.yaml}. Payment is a step of order
 * placement, so there is no separate pay operation. {@code placeOrder} answers 201 for a new order and 200 with the
 * stored order when the same {@code Idempotency-Key} is sent again.
 */
@RestController
@RequestMapping("/api/v1")
public class CheckoutController {

    private final CheckoutQuoteService quoteService;
    private final OrderService orderService;

    public CheckoutController(CheckoutQuoteService quoteService, OrderService orderService) {
        this.quoteService = quoteService;
        this.orderService = orderService;
    }

    @PostMapping("/checkout/quote")
    public CheckoutQuote quoteCheckout(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @Valid @RequestBody CheckoutQuoteRequest request) {
        return quoteService.quote(customer.id(), request.pointsToRedeem());
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, @Valid @RequestBody PlaceOrderRequest request) {
        PlaceOrderResult result = orderService.placeOrder(customer.id(), idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok(result.order());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/orders/" + result.order().id())).body(result.order());
    }
}
