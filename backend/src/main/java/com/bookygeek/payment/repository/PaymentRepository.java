package com.bookygeek.payment.repository;

import com.bookygeek.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The attempt made with this {@code Idempotency-Key} by this customer, if any. */
    Optional<Payment> findByCustomerIdAndIdempotencyKey(long customerId, UUID idempotencyKey);

    /** The payment of an order (at most one: {@code UNIQUE (order_id)}). */
    Optional<Payment> findByOrderId(long orderId);
}
