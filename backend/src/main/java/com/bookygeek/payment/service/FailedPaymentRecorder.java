package com.bookygeek.payment.service;

import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.entity.Payment;
import com.bookygeek.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/**
 * Writes the {@code FAILED} row of a declined payment in its own transaction ({@code REQUIRES_NEW}). The order
 * transaction rolls back on a decline, so without a separate transaction the attempt could not be recorded
 * (FR-PAY-12). It is a separate bean because Spring applies transaction settings only to calls that go through the
 * proxy. The row holds the method, the amount and a reason code, never card or UPI details.
 */
@Service
public class FailedPaymentRecorder {

    private final PaymentRepository payments;
    private final Clock clock;

    public FailedPaymentRecorder(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long customerId, PaymentMethod method, BigDecimal amount, String failureCode, UUID key) {
        payments.saveAndFlush(Payment.failed(customerId, method, amount, failureCode, key, clock.instant()));
    }
}
