package com.bookygeek.unit.catalogue;

import com.bookygeek.catalogue.dto.BookDetail;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.entity.AuthorProfile;
import com.bookygeek.catalogue.entity.Book;
import com.bookygeek.catalogue.repository.AuthorProfileRepository;
import com.bookygeek.catalogue.repository.BookRepository;
import com.bookygeek.catalogue.service.CatalogueService;
import com.bookygeek.category.dto.CategoryRef;
import com.bookygeek.category.service.CategoryService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Book detail rules (T076): the delivery date comes from the injected clock (physical = today + 5 days, eBook =
 * today, whatever the stock), an unknown or unlisted book is {@code BOOK_NOT_FOUND}, the breadcrumb is the path to the
 * book's first category, and related books are looked up by the book's own categories and never include the book.
 */
class BookDetailServiceTest {

    private static final CategoryRef NON_FICTION = new CategoryRef(2, "Non-Fiction");
    private static final CategoryRef SELF_HELP = new CategoryRef(14, "Self Help");
    private static final CategoryRef SCIENCE = new CategoryRef(18, "Science");

    private BookRepository books;
    private CategoryService categories;

    private static Book book(long id, String format, Integer stock, boolean listed, Long... categoryIds) {
        Book book = BeanUtils.instantiateClass(Book.class);
        ReflectionTestUtils.setField(book, "id", id);
        ReflectionTestUtils.setField(book, "title", "Title " + id);
        ReflectionTestUtils.setField(book, "author", "Author");
        ReflectionTestUtils.setField(book, "description", "Description");
        ReflectionTestUtils.setField(book, "publisher", "Publisher");
        ReflectionTestUtils.setField(book, "format", format);
        ReflectionTestUtils.setField(book, "language", "English");
        ReflectionTestUtils.setField(book, "price", new BigDecimal("499"));
        ReflectionTestUtils.setField(book, "stockQuantity", stock);
        ReflectionTestUtils.setField(book, "frontCoverUrl", "/covers/" + id + "-front.svg");
        ReflectionTestUtils.setField(book, "backCoverUrl", "/covers/" + id + "-back.svg");
        ReflectionTestUtils.setField(book, "listed", listed);
        ReflectionTestUtils.setField(book, "categoryIds", new TreeSet<>(Set.of(categoryIds)));
        return book;
    }

    private static AuthorProfile profile(String bio) {
        AuthorProfile profile = BeanUtils.instantiateClass(AuthorProfile.class);
        ReflectionTestUtils.setField(profile, "name", "Author");
        ReflectionTestUtils.setField(profile, "bio", bio);
        return profile;
    }

    private static CatalogueService service(BookRepository books, CategoryService categories, String today) {
        return service(books, mock(AuthorProfileRepository.class), categories, today);
    }

    private static CatalogueService service(BookRepository books, AuthorProfileRepository profiles,
            CategoryService categories, String today) {
        BookygeekProperties properties = new BookygeekProperties(new BookygeekProperties.Rewards(1000, 50),
                new BookygeekProperties.Tax(new BigDecimal("0.05")),
                new BookygeekProperties.Delivery(new BigDecimal("40.00")),
                new BookygeekProperties.Cancellation(48),
                List.of(new BookygeekProperties.PriceBand("All", null, null)));
        Clock clock = Clock.fixed(Instant.parse(today + "T10:00:00Z"), ZoneOffset.UTC);
        return new CatalogueService(books, profiles, categories, properties, clock);
    }

    @Test
    void theDetailCarriesTheWritersBiographyOrNullWhenThereIsNone() {
        when(books.findById(1L)).thenReturn(Optional.of(book(1, "PAPERBACK", 25, true, 14L)));
        AuthorProfileRepository profiles = mock(AuthorProfileRepository.class);
        when(profiles.findById("Author")).thenReturn(Optional.of(profile("A short biography.")));

        assertThat(service(books, profiles, categories, "2026-11-12").getBook(1).authorBio())
                .isEqualTo("A short biography.");
        assertThat(service(books, categories, "2026-11-12").getBook(1).authorBio()).isNull();
    }

    @BeforeEach
    void setUp() {
        books = mock(BookRepository.class);
        categories = mock(CategoryService.class);
        when(categories.refsByIds(anyCollection())).thenReturn(Map.of(14L, SELF_HELP, 18L, SCIENCE));
        when(categories.pathTo(14)).thenReturn(List.of(NON_FICTION, SELF_HELP));
    }

    @Test
    void aPhysicalBookIsDeliveredFiveDaysFromToday() {
        when(books.findById(1L)).thenReturn(Optional.of(book(1, "PAPERBACK", 25, true, 14L)));

        BookDetail detail = service(books, categories, "2026-11-12").getBook(1);

        assertThat(detail.deliveryDate()).isEqualTo("2026-11-17");
    }

    @Test
    void anEbookIsDeliveredToday() {
        when(books.findById(13L)).thenReturn(Optional.of(book(13, "EBOOK", null, true, 14L)));

        BookDetail detail = service(books, categories, "2026-11-12").getBook(13);

        assertThat(detail.deliveryDate()).isEqualTo("2026-11-12");
    }

    @Test
    void stockDoesNotChangeTheDeliveryDateAndZeroStockIsUnavailable() {
        when(books.findById(5L)).thenReturn(Optional.of(book(5, "HARDCOVER", 0, true, 14L)));

        BookDetail detail = service(books, categories, "2026-11-12").getBook(5);

        assertThat(detail.deliveryDate()).isEqualTo("2026-11-17");
        assertThat(detail.available()).isFalse();
        assertThat(detail.stockQuantity()).isZero();
    }

    @Test
    void theFiveDaysCrossAMonthAndYearEnd() {
        when(books.findById(1L)).thenReturn(Optional.of(book(1, "PAPERBACK", 3, true, 14L)));

        assertThat(service(books, categories, "2026-12-28").getBook(1).deliveryDate()).isEqualTo("2027-01-02");
    }

    @Test
    void anEbookShowsNoStockCountAndIsAlwaysAvailable() {
        Book ebook = book(13, "EBOOK", 7, true, 14L);
        when(books.findById(13L)).thenReturn(Optional.of(ebook));

        BookDetail detail = service(books, categories, "2026-11-12").getBook(13);

        assertThat(detail.format()).isEqualTo(BookFormat.EBOOK);
        assertThat(detail.available()).isTrue();
        assertThat(detail.stockQuantity()).isNull();
    }

    @Test
    void theDetailCarriesTheBackCoverTwoDecimalPriceAndBookCategories() {
        Book physical = book(1, "PAPERBACK", 25, true, 14L, 18L);
        when(books.findById(1L)).thenReturn(Optional.of(physical));

        BookDetail detail = service(books, categories, "2026-11-12").getBook(1);

        assertThat(detail.backCoverUrl()).isEqualTo("/covers/1-back.svg");
        assertThat(detail.frontCoverUrl()).isEqualTo("/covers/1-front.svg");
        assertThat(detail.price()).isEqualTo("499.00");
        assertThat(detail.categories()).containsExactly(SELF_HELP, SCIENCE);
    }

    @Test
    void theBreadcrumbIsThePathToTheFirstCategoryOfTheBook() {
        when(books.findById(11L)).thenReturn(Optional.of(book(11, "HARDCOVER", 8, true, 18L, 14L)));

        BookDetail detail = service(books, categories, "2026-11-12").getBook(11);

        assertThat(detail.breadcrumb()).containsExactly(NON_FICTION, SELF_HELP);
        verify(categories).pathTo(14);
    }

    @Test
    void aBookWithoutACategoryHasAnEmptyBreadcrumb() {
        when(books.findById(1L)).thenReturn(Optional.of(book(1, "PAPERBACK", 3, true)));

        assertThat(service(books, categories, "2026-11-12").getBook(1).breadcrumb()).isEmpty();
        verify(categories, never()).pathTo(anyLong());
    }

    @Test
    void anUnknownOrUnlistedBookIsBookNotFound() {
        when(books.findById(99L)).thenReturn(Optional.empty());
        when(books.findById(3L)).thenReturn(Optional.of(book(3, "PAPERBACK", 3, false, 14L)));
        CatalogueService service = service(books, categories, "2026-11-12");

        for (long id : new long[] {99, 3}) {
            assertThatThrownBy(() -> service.getBook(id)).isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND);
            assertThatThrownBy(() -> service.listRelatedBooks(id)).isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.BOOK_NOT_FOUND);
        }
    }

    @Test
    void relatedBooksAreLookedUpByTheCategoriesOfTheBookAndMappedWithTheDeliveryRule() {
        Book shown = book(1, "PAPERBACK", 25, true, 14L);
        Book related = book(12, "EBOOK", null, true, 14L);
        when(books.findById(1L)).thenReturn(Optional.of(shown));
        when(books.findRelated(1L, shown.getCategoryIds())).thenReturn(List.of(related));

        List<BookSummary> result = service(books, categories, "2026-11-12").listRelatedBooks(1);

        assertThat(result).extracting(BookSummary::id).containsExactly(12L);
        assertThat(result.get(0).deliveryDate()).isEqualTo("2026-11-12");
        assertThat(result.get(0).stockQuantity()).isNull();
        verify(books).findRelated(1L, Set.of(14L));
    }

    @Test
    void aBookWithoutACategoryHasNoRelatedBooksAndNothingIsQueried() {
        when(books.findById(1L)).thenReturn(Optional.of(book(1, "PAPERBACK", 3, true)));

        assertThat(service(books, categories, "2026-11-12").listRelatedBooks(1)).isEmpty();
        verify(books, never()).findRelated(anyLong(), anyCollection());
    }

    @Test
    void noSameCategoryBooksGivesAnEmptyList() {
        Book shown = book(54, "PAPERBACK", 3, true, 19L);
        when(books.findById(54L)).thenReturn(Optional.of(shown));
        when(books.findRelated(54L, shown.getCategoryIds())).thenReturn(List.of());

        assertThat(service(books, categories, "2026-11-12").listRelatedBooks(54)).isEmpty();
    }
}
