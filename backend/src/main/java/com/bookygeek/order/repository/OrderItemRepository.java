package com.bookygeek.order.repository;

import com.bookygeek.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, OrderItem.Key> {

    /** The lines of an order in a stable order (by book id). */
    List<OrderItem> findByOrderIdOrderByBookId(long orderId);

    /** The lines of several orders in one query, grouped by order and ordered by book id. */
    List<OrderItem> findByOrderIdInOrderByOrderIdAscBookIdAsc(Collection<Long> orderIds);

    /** Every book the customer has ordered in any order (placed or cancelled). */
    @Query("select distinct i.bookId from OrderItem i "
            + "where i.orderId in (select o.id from OrderEntity o where o.customerId = :customerId)")
    List<Long> findOrderedBookIds(@Param("customerId") long customerId);
}
