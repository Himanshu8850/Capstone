package com.bookygeek.unit.payment;

import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.payment.dto.PaymentMethod;
import com.bookygeek.payment.dto.PaymentRequest;
import com.bookygeek.payment.dto.PaymentStatus;
import com.bookygeek.payment.entity.Payment;
import com.bookygeek.payment.repository.PaymentRepository;
import com.bookygeek.payment.service.FailedPaymentRecorder;
import com.bookygeek.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The payment simulator (T097, PL-06): card {@code 4111-1111-1111-1111} approves, {@code 4000-0000-0000-0002}
 * declines, any other well-formed card is approved, UPI {@code demo@upi} approves and {@code failure@upi} declines,
 * COD is {@code PENDING_ON_DELIVERY} with no details, and no raw card data is stored, logged or printed.
 */
class PaymentServiceTest {

    private static final long CUSTOMER = 7L;
    private static final UUID KEY = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");
    private static final BigDecimal AMOUNT = new BigDecimal("1090.00");
    /** 12 November 2026: a card expiring 12/2026 is still valid, 11/2026 is not. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:15:30Z"), ZoneOffset.UTC);

    private PaymentRepository payments;
    private FailedPaymentRecorder failed;
    private PaymentService service;

    private static PaymentRequest card(String number, String expiry) {
        return new PaymentRequest(new PaymentRequest.Card(number, "Asha Verma", "123", expiry), null);
    }

    private static PaymentRequest upi(String id) {
        return new PaymentRequest(null, new PaymentRequest.Upi(id));
    }

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        failed = mock(FailedPaymentRecorder.class);
        service = new PaymentService(payments, failed, CLOCK);
    }

    // ---- cards ----------------------------------------------------------------------------------------------------

    @Test
    void theDocumentedSampleCardIsApprovedForCreditAndDebit() {
        for (PaymentMethod method : List.of(PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD)) {
            assertThat(service.authorise(CUSTOMER, method, card("4111-1111-1111-1111", "12/2030"), AMOUNT, KEY))
                    .isEqualTo(PaymentStatus.PAID);
        }
        verifyNoInteractions(failed);
    }

    @Test
    void anyOtherWellFormedCardIsApproved() {
        assertThat(service.authorise(CUSTOMER, PaymentMethod.CREDIT_CARD, card("5500-0000-0000-0004", "12/2030"),
                AMOUNT, KEY)).isEqualTo(PaymentStatus.PAID);
        assertThat(service.authorise(CUSTOMER, PaymentMethod.DEBIT_CARD, card("1234-5678-9012-3456", "01/2031"),
                AMOUNT, KEY)).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void theDeclineCardIsDeclinedAndTheAttemptIsRecordedAsFailed() {
        assertThatThrownBy(() -> service.authorise(CUSTOMER, PaymentMethod.CREDIT_CARD,
                card("4000-0000-0000-0002", "12/2030"), AMOUNT, KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED));
        verify(failed).record(eq(CUSTOMER), eq(PaymentMethod.CREDIT_CARD), eq(AMOUNT), eq("SIMULATED_DECLINE"),
                eq(KEY));
        verify(payments, never()).save(any());
    }

    @Test
    void aCardThatExpiresThisMonthIsValidButLastMonthIsNot() {
        assertThat(service.authorise(CUSTOMER, PaymentMethod.CREDIT_CARD, card("4111-1111-1111-1111", "11/2026"),
                AMOUNT, KEY)).isEqualTo(PaymentStatus.PAID);
        assertThatThrownBy(() -> service.authorise(CUSTOMER, PaymentMethod.CREDIT_CARD,
                card("4111-1111-1111-1111", "10/2026"), AMOUNT, KEY))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(e.getFieldErrors()).extracting(f -> f.field()).containsExactly("payment.card.expiry");
                });
        verifyNoInteractions(failed);
    }

    // ---- UPI ------------------------------------------------------------------------------------------------------

    @Test
    void demoUpiIsApprovedAndOtherWellFormedIdsToo() {
        assertThat(service.authorise(CUSTOMER, PaymentMethod.UPI, upi("demo@upi"), AMOUNT, KEY))
                .isEqualTo(PaymentStatus.PAID);
        assertThat(service.authorise(CUSTOMER, PaymentMethod.UPI, upi("asha.verma@okbank"), AMOUNT, KEY))
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void failureUpiIsDeclinedAndRecorded() {
        assertThatThrownBy(() -> service.authorise(CUSTOMER, PaymentMethod.UPI, upi("failure@upi"), AMOUNT, KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PAYMENT_DECLINED));
        verify(failed).record(eq(CUSTOMER), eq(PaymentMethod.UPI), eq(AMOUNT), anyString(), eq(KEY));
    }

    // ---- COD ------------------------------------------------------------------------------------------------------

    @Test
    void codIsPendingOnDeliveryWithoutAnyDetails() {
        assertThat(service.authorise(CUSTOMER, PaymentMethod.COD, null, AMOUNT, KEY))
                .isEqualTo(PaymentStatus.PENDING_ON_DELIVERY);
        verifyNoInteractions(failed);
    }

    @Test
    void codRejectsPaymentDetails() {
        assertThatThrownBy(() -> service.authorise(CUSTOMER, PaymentMethod.COD, upi("demo@upi"), AMOUNT, KEY))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    // ---- details must match the method -----------------------------------------------------------------------------

    @Test
    void detailsThatDoNotMatchTheMethodAreRejectedBeforeAnyPayment() {
        for (Runnable mismatch : List.<Runnable>of(
                () -> service.validateDetails(PaymentMethod.CREDIT_CARD, null),
                () -> service.validateDetails(PaymentMethod.DEBIT_CARD, upi("demo@upi")),
                () -> service.validateDetails(PaymentMethod.UPI, null),
                () -> service.validateDetails(PaymentMethod.UPI, card("4111-1111-1111-1111", "12/2030")),
                () -> service.validateDetails(PaymentMethod.CREDIT_CARD, new PaymentRequest(
                        new PaymentRequest.Card("4111-1111-1111-1111", "Asha Verma", "123", "12/2030"),
                        new PaymentRequest.Upi("demo@upi"))))) {
            assertThatThrownBy(mismatch::run).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        verifyNoInteractions(failed, payments);
    }

    // ---- nothing about the card is stored, logged or printed -------------------------------------------------------

    @Test
    void theStoredPaymentRowKeepsOnlyMethodStatusAmountAndKey() {
        service.record(CUSTOMER, 11L, PaymentMethod.CREDIT_CARD, PaymentStatus.PAID, AMOUNT, KEY);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        assertThat(saved.getValue().getOrderId()).isEqualTo(11L);
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(KEY);
    }

    @Test
    void thePaymentEntityHasNoFieldThatCouldHoldCardOrUpiData() {
        List<String> fields = Arrays.stream(Payment.class.getDeclaredFields()).map(Field::getName).toList();
        assertThat(fields).containsExactlyInAnyOrder("id", "customerId", "orderId", "method", "status", "amount",
                "failureCode", "idempotencyKey", "createdAt", "updatedAt");
    }

    @Test
    void aFailedAttemptIsRecordedWithoutCardDataAndWithoutAnOrder() {
        Payment attempt = Payment.failed(CUSTOMER, PaymentMethod.CREDIT_CARD, AMOUNT, "SIMULATED_DECLINE", KEY,
                CLOCK.instant());
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(attempt.getOrderId()).isNull();
        assertThat(attempt.getFailureCode()).isEqualTo("SIMULATED_DECLINE");
    }

    @Test
    void cardAndUpiDetailsAreMaskedWhenPrinted() {
        PaymentRequest request = card("4111-1111-1111-1111", "12/2030");
        assertThat(request.toString()).doesNotContain("4111").doesNotContain("123").doesNotContain("Asha")
                .doesNotContain("12/2030");
        assertThat(upi("demo@upi").toString()).doesNotContain("demo");
    }

    @Test
    void validationMessagesNeverEchoWhatWasTyped() {
        assertThatThrownBy(() -> service.validateDetails(PaymentMethod.CREDIT_CARD,
                card("4111-1111-1111-1111", "01/2020")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getMessage()).doesNotContain("4111");
                    e.getFieldErrors().forEach(f -> assertThat(f.message()).doesNotContain("4111")
                            .doesNotContain("01/2020"));
                });
        verify(failed, never()).record(anyLong(), any(), any(), anyString(), any());
    }
}
