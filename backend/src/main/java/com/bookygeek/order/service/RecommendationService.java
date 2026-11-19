package com.bookygeek.order.service;

import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.category.dto.CategoryRef;
import com.bookygeek.order.repository.OrderItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * "Recommended for You" and the basket recommendations (FR-BSK-07, FR-CAT-09, FR-LAND-03): listed, available books in
 * the same categories as the books the customer has previously ordered, excluding the books already in the basket.
 * A customer with no order history gets an empty list (the screens then show nothing); guests never call this. The
 * Order module hosts it because it needs the order history together with the basket and the catalogue.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    /** One page of books (the catalogue page size) is plenty for a recommendation strip. */
    static final int LIMIT = 12;

    private final OrderItemRepository orderItems;
    private final CartService cart;
    private final CatalogueService catalogue;

    public RecommendationService(OrderItemRepository orderItems, CartService cart, CatalogueService catalogue) {
        this.orderItems = orderItems;
        this.cart = cart;
        this.catalogue = catalogue;
    }

    public List<BookSummary> recommendationsFor(long customerId) {
        List<Long> orderedBookIds = orderItems.findOrderedBookIds(customerId);
        if (orderedBookIds.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = catalogue.summariesByIds(orderedBookIds).stream()
                .flatMap(book -> book.categories().stream())
                .map(CategoryRef::id)
                .collect(Collectors.toCollection(TreeSet::new));
        BasketResponse basket = cart.getBasket(customerId);
        Set<Long> inBasket = basket.items().stream().map(line -> line.book().id()).collect(Collectors.toSet());
        return catalogue.recommendedBooks(categoryIds, inBasket, LIMIT);
    }
}
