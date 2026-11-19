package com.bookygeek.catalogue.repository;

import com.bookygeek.catalogue.entity.Book;

import java.util.Comparator;
import java.util.Locale;

/**
 * Relevance ordering for a search (PL-07): title equals the term, then title starts with it, then title contains it,
 * then only the author contains it. Ties are broken by availability (available first), then title A to Z, then id.
 * Matching ignores letter case. Pure and free of persistence, so the ordering rules are unit-tested directly.
 */
public final class RelevanceOrdering {

    private RelevanceOrdering() {
    }

    /** Rank of a book for a lower-cased, trimmed search term: 0 best, 3 author-only match. */
    static int rank(Book book, String term) {
        String title = book.getTitle().toLowerCase(Locale.ROOT);
        if (title.equals(term)) {
            return 0;
        }
        if (title.startsWith(term)) {
            return 1;
        }
        if (title.contains(term)) {
            return 2;
        }
        return 3;
    }

    public static Comparator<Book> forTerm(String term) {
        return Comparator
                .comparingInt((Book book) -> rank(book, term))
                .thenComparing(book -> book.isAvailable() ? 0 : 1)
                .thenComparing(book -> book.getTitle().toLowerCase(Locale.ROOT))
                .thenComparing(Book::getId);
    }
}
