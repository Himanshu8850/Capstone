package com.bookygeek.payment.service;

import com.bookygeek.common.error.ApiError;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentRecord;
import com.bookygeek.payment.dto.PaymentRequest;
import com.bookygeek.payment.dto.PaymentStatus;
import com.bookygeek.payment.entity.Payment;
import com.bookygeek.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The payment simulator (PL-06, FR-PAY-04..08, FR-PAY-12/13). No gateway is called and no money moves; the outcome is
 * decided by fixed test values, so every run is repeatable:
 * <ul>
 *   <li>card: {@code 4000-0000-0000-0002} is declined, every other well-formed card with an unexpired date is
 *       approved ({@code 4111-1111-1111-1111} is the documented sample);</li>
 *   <li>UPI: {@code failure@upi} is declined, every other well-formed ID is approved ({@code demo@upi} is the sample);</li>
 *   <li>COD: no details, the payment is {@code PENDING_ON_DELIVERY} and nothing is collected.</li>
 * </ul>
 * Card and UPI details are read from the request and dropped: they are never stored, logged or returned.
 */
@Service
public class PaymentService {

    /** The card number of the simulated decline. */
    static final String DECLINE_CARD = "4000-0000-0000-0002";
    /** The UPI ID of the simulated decline. */
    static final String DECLINE_UPI = "failure@upi";
    /** Reason code stored on a declined attempt. */
    static final String DECLINED_CODE = "SIMULATED_DECLINE";

    private final PaymentRepository payments;
    private final FailedPaymentRecorder failedRecorder;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, FailedPaymentRecorder failedRecorder, Clock clock) {
        this.payments = payments;
        this.failedRecorder = failedRecorder;
        this.clock = clock;
    }

    /**
     * Checks that the details match the method (cards need {@code card} only, UPI needs {@code upi} only, COD needs
     * none) and that a card has not expired. A mismatch or an expired card is {@code VALIDATION_FAILED}; the field
     * messages never contain what the customer typed.
     */
    public void validateDetails(PaymentMethod method, PaymentRequest details) {
        boolean hasCard = details != null && details.card() != null;
        boolean hasUpi = details != null && details.upi() != null;
        switch (method) {
            case CREDIT_CARD, DEBIT_CARD -> {
                if (!hasCard || hasUpi) {
                    throw invalid("payment", "Enter the card details.");
                }
                if (expired(details.card().expiry())) {
                    throw invalid("payment.card.expiry", "This card has expired.");
                }
            }
            case UPI -> {
                if (!hasUpi || hasCard) {
                    throw invalid("payment", "Enter your UPI ID.");
                }
            }
            case COD -> {
                if (details != null) {
                    throw invalid("payment", "Cash on delivery needs no payment details.");
                }
            }
        }
    }

    /**
     * Runs the simulated payment and returns the resulting state: {@code PAID} for an approved card or UPI payment,
     * {@code PENDING_ON_DELIVERY} for COD. A decline is recorded as a {@code FAILED} attempt in its own transaction
     * and reported as {@code PAYMENT_DECLINED}; the caller's transaction then rolls back.
     */
    public PaymentStatus authorise(long customerId, PaymentMethod method, PaymentRequest details, BigDecimal amount,
            UUID idempotencyKey) {
        validateDetails(method, details);
        if (method == PaymentMethod.COD) {
            return PaymentStatus.PENDING_ON_DELIVERY;
        }
        boolean approved = method.isCard()
                ? !DECLINE_CARD.equals(details.card().number())
                : !DECLINE_UPI.equalsIgnoreCase(details.upi().upiId());
        if (!approved) {
            failedRecorder.record(customerId, method, amount, DECLINED_CODE, idempotencyKey);
            throw new ApiException(ErrorCode.PAYMENT_DECLINED);
        }
        return PaymentStatus.PAID;
    }

    /** Stores the approved payment of a placed order (state as returned by {@link #authorise}). */
    @Transactional
    public void record(long customerId, long orderId, PaymentMethod method, PaymentStatus status, BigDecimal amount,
            UUID idempotencyKey) {
        payments.save(Payment.forOrder(customerId, orderId, method, status, amount, idempotencyKey, clock.instant()));
    }

    /**
     * Settles the payment of a cancelled order: a {@code PAID} payment becomes {@code REFUNDED} (a simulated record,
     * no money moves) and a Cash on Delivery payment becomes {@code CANCELLED}. Any other state is a data error and
     * rolls the cancellation back.
     */
    @Transactional
    public PaymentStatus settleCancelledOrder(long orderId) {
        Payment payment = payments.findByOrderId(orderId).orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));
        PaymentStatus settled = switch (payment.getStatus()) {
            case PAID -> PaymentStatus.REFUNDED;
            case PENDING_ON_DELIVERY -> PaymentStatus.CANCELLED;
            default -> throw new ApiException(ErrorCode.INTERNAL_ERROR);
        };
        payment.changeStatus(settled, clock.instant());
        return settled;
    }

    /** The attempt this customer made with this key, whether it succeeded (has an order) or was declined. */
    @Transactional(readOnly = true)
    public Optional<PaymentRecord> findByIdempotencyKey(long customerId, UUID idempotencyKey) {
        return payments.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).map(PaymentService::toRecord);
    }

    /** The payment of an order. */
    @Transactional(readOnly = true)
    public Optional<PaymentRecord> findByOrderId(long orderId) {
        return payments.findByOrderId(orderId).map(PaymentService::toRecord);
    }

    private boolean expired(String expiry) {
        int month = Integer.parseInt(expiry.substring(0, 2));
        int year = Integer.parseInt(expiry.substring(3));
        return YearMonth.of(year, month).isBefore(YearMonth.now(clock));
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, List.of(new ApiError.FieldError(field, message)));
    }

    private static PaymentRecord toRecord(Payment payment) {
        return new PaymentRecord(payment.getOrderId(), payment.getMethod(), payment.getStatus());
    }
}
