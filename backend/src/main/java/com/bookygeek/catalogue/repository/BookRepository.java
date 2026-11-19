package com.bookygeek.catalogue.repository;

import com.bookygeek.catalogue.dto.Brand;
import com.bookygeek.catalogue.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long>, BookSearch {

    /** Publishers (brands) of listed books with their listed-book counts, in alphabetical order. */
    @Query("select new com.bookygeek.catalogue.dto.Brand(b.publisher, count(b)) from Book b "
            + "where b.listed = true group by b.publisher order by b.publisher")
    List<Brand> findBrands();

    /** Languages present in the listed books, alphabetically. */
    @Query("select distinct b.language from Book b where b.listed = true order by b.language")
    List<String> findLanguages();

    /**
     * Listed books, other than {@code bookId}, that share at least one of the given categories: available books first,
     * then newest, then by id (the same default order as the catalogue).
     */
    @Query("select b from Book b where b.listed = true and b.id <> :bookId "
            + "and b.id in (select r.id from Book r join r.categoryIds c where c in :categoryIds) "
            + "order by case when b.format = 'EBOOK' or b.stockQuantity > 0 then 0 else 1 end, b.createdAt desc, b.id")
    List<Book> findRelated(@Param("bookId") long bookId, @Param("categoryIds") Collection<Long> categoryIds);

    /**
     * Locks the listed books among {@code ids} ({@code SELECT … FOR UPDATE}, ascending id, so concurrent orders never
     * deadlock) and returns the committed price and stock. A native scalar query is used on purpose: it always reads
     * the database, never an entity already cached in the persistence context.
     */
    @Query(value = "select id as id, title as title, format as format, price as price, stock_quantity as stock "
            + "from book where id in (:ids) and listed = true order by id for update", nativeQuery = true)
    List<PurchaseRow> lockForPurchase(@Param("ids") Collection<Long> ids);

    /**
     * Listed, available books (an eBook, or a physical book with stock) that share at least one of the given
     * categories: newest first, then by id. The caller removes the books it does not want to recommend.
     */
    @Query("select b from Book b where b.listed = true and (b.format = 'EBOOK' or b.stockQuantity > 0) "
            + "and b.id in (select r.id from Book r join r.categoryIds c where c in :categoryIds) "
            + "order by b.createdAt desc, b.id")
    List<Book> findAvailableInCategories(@Param("categoryIds") Collection<Long> categoryIds);

    /**
     * Gives {@code quantity} units back to a physical book's stock (a cancelled order). Returns 0 for an eBook, which
     * has no stock. The persistence context is cleared so later reads see the new stock; the caller must flush any
     * pending change first.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update book set stock_quantity = stock_quantity + :quantity "
            + "where id = :id and format <> 'EBOOK'", nativeQuery = true)
    int incrementStock(@Param("id") long id, @Param("quantity") int quantity);

    /**
     * Takes {@code quantity} units from a physical book's stock. Returns 0 when the book is an eBook or has fewer
     * units, so stock can never go below zero. The persistence context is cleared so later reads see the new stock.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update book set stock_quantity = stock_quantity - :quantity "
            + "where id = :id and format <> 'EBOOK' and stock_quantity >= :quantity", nativeQuery = true)
    int decrementStock(@Param("id") long id, @Param("quantity") int quantity);
}
