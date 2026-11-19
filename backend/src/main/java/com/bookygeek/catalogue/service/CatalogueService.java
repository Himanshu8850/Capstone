package com.bookygeek.catalogue.service;

import com.bookygeek.catalogue.dto.BookDetail;
import com.bookygeek.catalogue.dto.BookFormat;
import com.bookygeek.catalogue.dto.BookPage;
import com.bookygeek.catalogue.dto.BookQuery;
import com.bookygeek.catalogue.dto.BookSort;
import com.bookygeek.catalogue.dto.BookSummary;
import com.bookygeek.catalogue.dto.Brand;
import com.bookygeek.catalogue.dto.FilterOptions;
import com.bookygeek.catalogue.dto.PurchaseBook;
import com.bookygeek.catalogue.entity.AuthorProfile;
import com.bookygeek.catalogue.entity.Book;
import com.bookygeek.catalogue.repository.AuthorProfileRepository;
import com.bookygeek.catalogue.repository.BookRepository;
import com.bookygeek.category.dto.CategoryRef;
import com.bookygeek.category.service.CategoryService;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.props.BookygeekProperties;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Catalogue reads: filtering, price bands, relevance ordering (PL-07) and the fixed page size of 12. Only listed books
 * are ever returned. The delivery date comes from the injected {@link Clock}: today plus 5 days for a physical book,
 * today for an eBook.
 */
@Service
@Transactional(readOnly = true)
public class CatalogueService {

    /** Days between today and the delivery of a physical book. */
    static final int PHYSICAL_DELIVERY_DAYS = 5;

    private final BookRepository books;
    private final AuthorProfileRepository authorProfiles;
    private final CategoryService categories;
    private final BookygeekProperties properties;
    private final Clock clock;

    public CatalogueService(BookRepository books, AuthorProfileRepository authorProfiles, CategoryService categories,
            BookygeekProperties properties, Clock clock) {
        this.books = books;
        this.authorProfiles = authorProfiles;
        this.categories = categories;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * One page of listed books. All filters are optional and ANDed; {@code category} selects the whole subtree of that
     * category. Blank text filters are treated as absent.
     */
    public BookPage listBooks(Long category, String brand, String q, String author, String language,
            BookFormat format, BigDecimal minPrice, BigDecimal maxPrice, BookSort sort, int page) {
        BookQuery query = new BookQuery(
                category == null ? null : categories.subtreeIds(category),
                blankToNull(brand),
                normaliseSearchTerm(q),
                blankToNull(author),
                blankToNull(language),
                format,
                minPrice,
                maxPrice,
                sort == null ? BookSort.RELEVANCE : sort,
                Math.max(page, 0));
        Page<Book> result = books.search(query);
        return new BookPage(summaries(result.getContent()), query.page(), BookQuery.PAGE_SIZE,
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * One listed book with its back cover, breadcrumb and the writer's biography (null when there is none). The
     * breadcrumb is the path to the book's first category (lowest id), from the top-level category down. An unknown
     * or unlisted id is {@code BOOK_NOT_FOUND}.
     */
    public BookDetail getBook(long id) {
        Book book = listedBook(id);
        List<CategoryRef> breadcrumb = book.getCategoryIds().stream().findFirst()
                .map(categories::pathTo).orElse(List.of());
        String authorBio = authorProfiles.findById(book.getAuthor()).map(AuthorProfile::getBio).orElse(null);
        return BookDetail.of(summaries(List.of(book)).get(0), book.getBackCoverUrl(), breadcrumb, authorBio);
    }

    /**
     * The other listed books that share a category with this book, never the book itself (OD-7). An unknown or
     * unlisted id is {@code BOOK_NOT_FOUND}; a book with no same-category books gives an empty list.
     */
    public List<BookSummary> listRelatedBooks(long id) {
        Book book = listedBook(id);
        if (book.getCategoryIds().isEmpty()) {
            return List.of();
        }
        return summaries(books.findRelated(id, book.getCategoryIds()));
    }

    private Book listedBook(long id) {
        return books.findById(id).filter(Book::isListed)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND));
    }

    /** Publishers of listed books with their book counts, alphabetically. */

    public List<Brand> listPublishers() {
        return books.findBrands();
    }

    /** Languages present in the listed books, the three formats and the configured price bands. */
    public FilterOptions filterOptions() {
        List<FilterOptions.PriceBand> bands = properties.priceBands().stream()
                .map(b -> new FilterOptions.PriceBand(b.label(), money(b.minPrice()), money(b.maxPrice())))
                .toList();
        return new FilterOptions(books.findLanguages(), Arrays.asList(BookFormat.values()), bands);
    }

    /**
     * Summaries of the listed books among {@code ids}, in id order. Unknown and unlisted ids are left out. Used by the
     * Cart module, which never sees the {@code Book} entity.
     */
    public List<BookSummary> listedSummaries(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return summaries(books.findAllById(ids).stream()
                .filter(Book::isListed)
                .sorted(Comparator.comparing(Book::getId))
                .toList());
    }

    /**
     * Summaries of the books among {@code ids}, listed or not, in id order. Order history uses it so a past order
     * still shows a book that has since left the catalogue.
     */
    public List<BookSummary> summariesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return summaries(books.findAllById(ids).stream().sorted(Comparator.comparing(Book::getId)).toList());
    }

    /**
     * Locks the listed books among {@code ids} for an order ({@code SELECT … FOR UPDATE} in ascending id order) and
     * returns their committed price and stock. The locks last until the caller's transaction ends.
     */
    @Transactional
    public List<PurchaseBook> lockForPurchase(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return books.lockForPurchase(ids).stream()
                .map(row -> new PurchaseBook(row.getId(), row.getTitle(), BookFormat.valueOf(row.getFormat()),
                        row.getPrice(), row.getStock()))
                .toList();
    }

    /**
     * Takes the given units from the stock of physical books (ascending id order). The caller has locked the rows;
     * a shortfall is {@code OUT_OF_STOCK} and the whole transaction rolls back. eBooks must not be passed.
     */
    @Transactional
    public void decrementStock(Map<Long, Integer> quantities) {
        for (Map.Entry<Long, Integer> entry : new TreeMap<>(quantities).entrySet()) {
            if (books.decrementStock(entry.getKey(), entry.getValue()) == 0) {
                throw new ApiException(ErrorCode.OUT_OF_STOCK);
            }
        }
    }

    /**
     * Gives the units of a cancelled order back to the stock of physical books (ascending id order, so concurrent
     * cancellations and orders lock the rows in the same order). eBooks have no stock and are ignored.
     */
    @Transactional
    public void incrementStock(Map<Long, Integer> quantities) {
        for (Map.Entry<Long, Integer> entry : new TreeMap<>(quantities).entrySet()) {
            books.incrementStock(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Listed, available books in any of {@code categoryIds}, newest first, without the {@code excludedBookIds} and at
     * most {@code limit} of them. The Order module feeds it the categories of a customer's earlier orders.
     */
    public List<BookSummary> recommendedBooks(Collection<Long> categoryIds, Collection<Long> excludedBookIds, int limit) {
        if (categoryIds.isEmpty() || limit < 1) {
            return List.of();
        }
        return summaries(books.findAvailableInCategories(categoryIds).stream()
                .filter(book -> !excludedBookIds.contains(book.getId()))
                .limit(limit)
                .toList());
    }

    /** Maps books to the contract's summary; also used by the book-detail and basket features. */
    public List<BookSummary> summaries(List<Book> list) {
        Set<Long> categoryIds = new HashSet<>();
        list.forEach(book -> categoryIds.addAll(book.getCategoryIds()));
        Map<Long, CategoryRef> refs = categories.refsByIds(categoryIds);
        return list.stream().map(book -> summary(book, refs)).toList();
    }

    private BookSummary summary(Book book, Map<Long, CategoryRef> refs) {
        List<CategoryRef> bookCategories = book.getCategoryIds().stream()
                .map(refs::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new BookSummary(book.getId(), book.getTitle(), book.getAuthor(), book.getDescription(),
                BookFormat.valueOf(book.getFormat()), book.getPublisher(), book.getLanguage(),
                money(book.getPrice()), book.getFrontCoverUrl(), bookCategories, book.isAvailable(),
                book.isEbook() ? null : book.getStockQuantity(), deliveryDate(book).toString());
    }

    /** Today plus 5 days for a physical book, today for an eBook (the same rule on every screen). */
    LocalDate deliveryDate(Book book) {
        LocalDate today = LocalDate.now(clock);
        return book.isEbook() ? today : today.plusDays(PHYSICAL_DELIVERY_DAYS);
    }

    static String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normaliseSearchTerm(String q) {
        String trimmed = blankToNull(q);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
