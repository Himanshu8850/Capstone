package com.bookygeek.order.repository;

import com.bookygeek.order.entity.OrderEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    /** An order only when it belongs to the customer; another customer's id looks like a missing one. */
    Optional<OrderEntity> findByIdAndCustomerId(long id, long customerId);

    /**
     * The customer's order locked with {@code SELECT … FOR UPDATE}, so two cancellations of one order are serialised
     * and the second one sees the first one's result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id and o.customerId = :customerId")
    Optional<OrderEntity> findForUpdate(@Param("id") long id, @Param("customerId") long customerId);

    /** All of the customer's orders, newest first (served by the index {@code (customer_id, placed_at DESC)}). */
    List<OrderEntity> findByCustomerIdOrderByPlacedAtDescIdDesc(long customerId);
}
