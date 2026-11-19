package com.bookygeek.cart.repository;

import com.bookygeek.cart.entity.BasketItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BasketItemRepository extends JpaRepository<BasketItem, BasketItem.Key> {

    /** The customer's lines in a stable order (by book id), so quantity changes never reorder the basket. */
    List<BasketItem> findByCustomerIdOrderByBookId(long customerId);

    Optional<BasketItem> findByCustomerIdAndBookId(long customerId, long bookId);
}
