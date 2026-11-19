package com.bookygeek.unit.catalogue;

import com.bookygeek.catalogue.entity.Book;
import com.bookygeek.catalogue.repository.LikePattern;
import com.bookygeek.catalogue.repository.RelevanceOrdering;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Relevance ordering of a search (PL-07, T062): title equals, then starts with, then contains, then author-only; ties
 * by availability, then title A to Z, then id. The default order without a search term is a database order and is
 * covered by {@code CatalogueQueryIT}.
 */
class RelevanceSortTest {

    private static Book book(long id, String title, String format, Integer stock) {
        Book book = BeanUtils.instantiateClass(Book.class);
        ReflectionTestUtils.setField(book, "id", id);
        ReflectionTestUtils.setField(book, "title", title);
        ReflectionTestUtils.setField(book, "author", "Someone");
        ReflectionTestUtils.setField(book, "format", format);
        ReflectionTestUtils.setField(book, "stockQuantity", stock);
        return book;
    }

    private static List<Long> order(String term, Book... books) {
        List<Book> list = new ArrayList<>(List.of(books));
        list.sort(RelevanceOrdering.forTerm(term));
        return list.stream().map(Book::getId).toList();
    }

    @Test
    void titleEqualsComesBeforeStartsWithBeforeContainsBeforeAuthorOnly() {
        Book authorOnly = book(1, "Zen Garden", "PAPERBACK", 5);
        Book contains = book(2, "The Habit", "PAPERBACK", 5);
        Book startsWith = book(3, "Habit Ladder", "PAPERBACK", 5);
        Book equals = book(4, "Habit", "PAPERBACK", 5);

        assertThat(order("habit", authorOnly, contains, startsWith, equals)).containsExactly(4L, 3L, 2L, 1L);
    }

    @Test
    void matchingIgnoresLetterCase() {
        Book equals = book(1, "HABIT", "PAPERBACK", 5);
        Book startsWith = book(2, "habit stacking", "PAPERBACK", 5);

        assertThat(order("habit", startsWith, equals)).containsExactly(1L, 2L);
    }

    @Test
    void withTheSameRankAnAvailableBookComesBeforeAnUnavailableOne() {
        Book outOfStock = book(1, "Habit A", "PAPERBACK", 0);
        Book inStock = book(2, "Habit B", "PAPERBACK", 3);
        Book ebook = book(3, "Habit C", "EBOOK", null);

        assertThat(order("habit", outOfStock, inStock, ebook)).containsExactly(2L, 3L, 1L);
    }

    @Test
    void withTheSameRankAndAvailabilityTitleAToZDecides() {
        Book b = book(1, "Habit B", "PAPERBACK", 3);
        Book a = book(2, "Habit A", "PAPERBACK", 3);

        assertThat(order("habit", b, a)).containsExactly(2L, 1L);
    }

    @Test
    void whenEverythingElseIsEqualTheLowerIdComesFirst() {
        Book second = book(9, "Habit", "PAPERBACK", 3);
        Book first = book(4, "Habit", "PAPERBACK", 3);

        assertThat(order("habit", second, first)).containsExactly(4L, 9L);
    }

    @Test
    void anUnavailableBookWithABetterRankStillComesFirst() {
        Book unavailableExact = book(1, "Habit", "PAPERBACK", 0);
        Book availablePartial = book(2, "The Habit Loop", "PAPERBACK", 9);

        assertThat(order("habit", availablePartial, unavailableExact)).containsExactly(1L, 2L);
    }

    @Test
    void likeWildcardsInSearchTextAreEscapedSoTheyMatchLiterally() {
        assertThat(LikePattern.escape("100%")).isEqualTo("100\\%");
        assertThat(LikePattern.escape("a_b")).isEqualTo("a\\_b");
        assertThat(LikePattern.escape("back\\slash")).isEqualTo("back\\\\slash");
        assertThat(LikePattern.escape("plain")).isEqualTo("plain");
    }
}
