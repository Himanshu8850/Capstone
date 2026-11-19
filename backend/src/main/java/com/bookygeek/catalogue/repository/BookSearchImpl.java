package com.bookygeek.catalogue.repository;

import com.bookygeek.catalogue.dto.BookQuery;
import com.bookygeek.catalogue.entity.Book;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria implementation of {@link BookSearch}. Every value reaches the database as a bound parameter; search text is
 * wildcard-escaped before it is used in {@code LIKE}. A relevance sort with a search term is ordered by
 * {@link RelevanceOrdering} (the matching books are few, so they are sorted in memory); every other order is done by
 * the database. Without a search term, relevance is available books first, then newest.
 */
class BookSearchImpl implements BookSearch {

    private static final char ESCAPE = '\\';

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<Book> search(BookQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Book> countRoot = countQuery.from(Book.class);
        countQuery.select(cb.count(countRoot)).where(filters(cb, countQuery, countRoot, query));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<Book> select = cb.createQuery(Book.class);
        Root<Book> root = select.from(Book.class);
        select.select(root).where(filters(cb, select, root, query));

        int offset = query.page() * BookQuery.PAGE_SIZE;
        List<Book> content;
        if (query.sort() == com.bookygeek.catalogue.dto.BookSort.RELEVANCE && query.searchTerm() != null) {
            List<Book> matches = new ArrayList<>(entityManager.createQuery(select).getResultList());
            matches.sort(RelevanceOrdering.forTerm(query.searchTerm()));
            content = matches.stream().skip(offset).limit(BookQuery.PAGE_SIZE).toList();
        } else {
            select.orderBy(order(cb, root, query));
            content = entityManager.createQuery(select)
                    .setFirstResult(offset)
                    .setMaxResults(BookQuery.PAGE_SIZE)
                    .getResultList();
        }
        return new PageImpl<>(content, PageRequest.of(query.page(), BookQuery.PAGE_SIZE), total);
    }

    private Predicate[] filters(CriteriaBuilder cb, CriteriaQuery<?> criteria, Root<Book> root, BookQuery query) {
        List<Predicate> all = new ArrayList<>();
        all.add(cb.isTrue(root.get("listed")));

        if (query.categoryIds() != null) {
            if (query.categoryIds().isEmpty()) {
                all.add(cb.disjunction());
            } else {
                Subquery<Long> inCategory = criteria.subquery(Long.class);
                Root<Book> other = inCategory.from(Book.class);
                Join<Book, Long> category = other.join("categoryIds");
                inCategory.select(other.<Long>get("id")).where(cb.equal(other.get("id"), root.get("id")),
                        category.in(query.categoryIds()));
                all.add(cb.exists(inCategory));
            }
        }
        if (query.brand() != null) {
            all.add(cb.equal(root.get("publisher"), query.brand()));
        }
        if (query.author() != null) {
            all.add(cb.equal(root.get("author"), query.author()));
        }
        if (query.language() != null) {
            all.add(cb.equal(root.get("language"), query.language()));
        }
        if (query.format() != null) {
            all.add(cb.equal(root.get("format"), query.format().name()));
        }
        if (query.minPrice() != null) {
            all.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), query.minPrice()));
        }
        if (query.maxPrice() != null) {
            all.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), query.maxPrice()));
        }
        if (query.searchTerm() != null) {
            String contains = "%" + LikePattern.escape(query.searchTerm()) + "%";
            all.add(cb.or(
                    cb.like(cb.lower(root.<String>get("title")), contains, ESCAPE),
                    cb.like(cb.lower(root.<String>get("author")), contains, ESCAPE)));
        }
        return all.toArray(Predicate[]::new);
    }

    private List<Order> order(CriteriaBuilder cb, Root<Book> root, BookQuery query) {
        Expression<Integer> unavailable = cb.<Integer>selectCase()
                .when(cb.equal(root.get("format"), Book.EBOOK), 0)
                .when(cb.greaterThan(root.<Integer>get("stockQuantity"), 0), 0)
                .otherwise(1);
        Order byTitle = cb.asc(cb.lower(root.<String>get("title")));
        Order byId = cb.asc(root.get("id"));

        return switch (query.sort()) {
            case PRICE_ASC -> List.of(cb.asc(root.get("price")), byTitle, byId);
            case PRICE_DESC -> List.of(cb.desc(root.get("price")), byTitle, byId);
            case TITLE_ASC -> List.of(byTitle, byId);
            case RELEVANCE -> List.of(cb.asc(unavailable), cb.desc(root.get("createdAt")), byId);
        };
    }
}
