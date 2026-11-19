package com.bookygeek.cart.controller;

import com.bookygeek.cart.dto.AddBasketItemRequest;
import com.bookygeek.cart.dto.BasketLinesRequest;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.dto.MergeResult;
import com.bookygeek.cart.dto.UpdateBasketItemRequest;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.common.security.AuthenticatedCustomer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code previewBasket}, {@code getBasket}, {@code addBasketItem}, {@code updateBasketItem}, {@code removeBasketItem}
 * and {@code mergeGuestBasket} exactly as in {@code api/openapi.yaml}. Only the preview is public; every other
 * operation works on the authenticated customer's own basket, so one customer can never reach another's lines.
 */
@RestController
@RequestMapping("/api/v1/basket")
public class BasketController {

    private final CartService cartService;

    public BasketController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/preview")
    public BasketResponse previewBasket(@Valid @RequestBody BasketLinesRequest request) {
        return cartService.preview(request.items());
    }

    @GetMapping
    public BasketResponse getBasket(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return cartService.getBasket(customer.id());
    }

    @PostMapping("/items")
    public BasketResponse addBasketItem(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @Valid @RequestBody AddBasketItemRequest request) {
        return cartService.addItem(customer.id(), request.bookId(), request.quantityOrDefault());
    }

    @PutMapping("/items/{bookId:\\d{1,18}}")
    public BasketResponse updateBasketItem(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable @Min(value = 1, message = "Choose a valid book.") long bookId,
            @Valid @RequestBody UpdateBasketItemRequest request) {
        return cartService.updateItem(customer.id(), bookId, request.quantity());
    }

    @DeleteMapping("/items/{bookId:\\d{1,18}}")
    public BasketResponse removeBasketItem(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable @Min(value = 1, message = "Choose a valid book.") long bookId) {
        return cartService.removeItem(customer.id(), bookId);
    }

    @PostMapping("/merge")
    public MergeResult mergeGuestBasket(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @Valid @RequestBody BasketLinesRequest request) {
        return cartService.merge(customer.id(), request.items());
    }
}
