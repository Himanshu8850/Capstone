package com.bookygeek.unit.order;

import com.bookygeek.cart.dto.BasketLine;
import com.bookygeek.cart.dto.BasketResponse;
import com.bookygeek.cart.service.CartService;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.category.dto.CategoryRef;
import com.bookygeek.order.repository.OrderItemRepository;
import com.bookygeek.order.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Recommendations (T116, FR-BSK-07, FR-CAT-09, FR-LAND-03): the categories of the books a customer has ordered feed the
 * catalogue query, books already in the basket are excluded, and a customer without order history gets nothing.
 */
class RecommendationServiceTest {

    private static final long CUSTOMER = 7L;
    private static final CategoryRef FICTION = new CategoryRef(1L, "Fiction");
    private static final CategoryRef SCIENCE = new CategoryRef(2L, "Science");
    private static final CategoryRef HISTORY = new CategoryRef(3L, "History");

    private OrderItemRepository orderItems;
    private CartService cart;
    private CatalogueService catalogue;
    private RecommendationService service;

    private static BookSummary summary(long id, CategoryRef... categories) {
        return new BookSummary(id, "Title " + id, "Author", "Description", BookFormat.PAPERBACK, "Publisher", "English",
                "500.00", "/covers/" + id + "-front.svg", List.of(categories), true, 5, "2026-11-17");
    }

    private static BasketResponse basketWith(long... bookIds) {
        List<BasketLine> lines = java.util.Arrays.stream(bookIds).mapToObj(id -> new BasketLine(summary(id), 1, "500.00"))
                .toList();
        return new BasketResponse(lines, lines.size(), "0.00", "0.00", "0.00", "0.00");
    }

    @BeforeEach
    void setUp() {
        orderItems = mock(OrderItemRepository.class);
        cart = mock(CartService.class);
        catalogue = mock(CatalogueService.class);
        service = new RecommendationService(orderItems, cart, catalogue);
        when(cart.getBasket(CUSTOMER)).thenReturn(basketWith());
    }

    @Test
    void aCustomerWithoutOrderHistoryGetsNothingAndNoOtherServiceIsAsked() {
        when(orderItems.findOrderedBookIds(CUSTOMER)).thenReturn(List.of());

        assertThat(service.recommendationsFor(CUSTOMER)).isEmpty();

        verifyNoInteractions(catalogue);
        verifyNoInteractions(cart);
    }

    @Test
    void theCategoriesOfEveryOrderedBookAreUsedOnceEach() {
        when(orderItems.findOrderedBookIds(CUSTOMER)).thenReturn(List.of(10L, 11L));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(
                summary(10L, FICTION, SCIENCE), summary(11L, SCIENCE)));
        List<BookSummary> expected = List.of(summary(20L, FICTION));
        when(catalogue.recommendedBooks(anyCollection(), anyCollection(), anyInt())).thenReturn(expected);

        List<BookSummary> result = service.recommendationsFor(CUSTOMER);

        assertThat(result).isEqualTo(expected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> categories = ArgumentCaptor.forClass(Collection.class);
        verify(catalogue).recommendedBooks(categories.capture(), anyCollection(), eq(12));
        assertThat(categories.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void booksAlreadyInTheBasketAreExcluded() {
        when(orderItems.findOrderedBookIds(CUSTOMER)).thenReturn(List.of(10L));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(summary(10L, HISTORY)));
        when(cart.getBasket(CUSTOMER)).thenReturn(basketWith(21L, 22L));
        when(catalogue.recommendedBooks(anyCollection(), anyCollection(), anyInt())).thenReturn(List.of());

        service.recommendationsFor(CUSTOMER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(catalogue).recommendedBooks(anyCollection(), excluded.capture(), eq(12));
        assertThat(excluded.getValue()).containsExactlyInAnyOrder(21L, 22L);
    }

    @Test
    void theResultIsAtMostOnePageOfBooks() {
        when(orderItems.findOrderedBookIds(CUSTOMER)).thenReturn(List.of(10L));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of(summary(10L, FICTION)));
        when(catalogue.recommendedBooks(anyCollection(), anyCollection(), anyInt())).thenReturn(List.of());

        service.recommendationsFor(CUSTOMER);

        verify(catalogue).recommendedBooks(anyCollection(), anyCollection(), eq(12));
    }

    @Test
    void orderedBooksThatAreNoLongerListedContributeNoCategoriesSoNothingIsRecommended() {
        when(orderItems.findOrderedBookIds(CUSTOMER)).thenReturn(List.of(10L));
        when(catalogue.summariesByIds(anyCollection())).thenReturn(List.of());
        when(catalogue.recommendedBooks(anyCollection(), anyCollection(), anyInt())).thenReturn(List.of());

        assertThat(service.recommendationsFor(CUSTOMER)).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> categories = ArgumentCaptor.forClass(Collection.class);
        verify(catalogue).recommendedBooks(categories.capture(), anyCollection(), eq(12));
        assertThat(categories.getValue()).isEmpty();
    }
}
