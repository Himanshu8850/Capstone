package com.bookygeek.unit.common;

import com.bookygeek.common.error.ApiError;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.error.GlobalExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:15:30Z");
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void everyErrorCodeMapsToItsContractStatusAndHasAGenericMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            ResponseEntity<ApiError> response = handler.handleApi(new ApiException(code));
            assertThat(response.getStatusCode()).isEqualTo(code.status());
            ApiError body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.status()).isEqualTo(code.status().value());
            assertThat(body.code()).isEqualTo(code.name());
            assertThat(body.message()).isNotBlank().isEqualTo(code.defaultMessage());
            assertThat(body.timestamp()).isEqualTo(NOW);
            assertThat(body.fieldErrors()).isNull();
        }
    }

    @Test
    void errorCodesMatchTheApprovedContractExactly() {
        assertThat(ErrorCode.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "VALIDATION_FAILED", "UNAUTHENTICATED", "INVALID_CREDENTIALS", "EMAIL_TAKEN", "NOT_FOUND",
                "BOOK_NOT_FOUND", "QUANTITY_LIMIT", "OUT_OF_STOCK", "EMPTY_BASKET", "BASKET_CHANGED",
                "POINTS_LIMIT_EXCEEDED", "PAYMENT_DECLINED", "CANCELLATION_WINDOW_EXPIRED", "ALREADY_CANCELLED",
                "ALREADY_REVIEWED",
                "INTERNAL_ERROR");
        assertThat(ErrorCode.VALIDATION_FAILED.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.UNAUTHENTICATED.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.INVALID_CREDENTIALS.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.BOOK_NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.INTERNAL_ERROR.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.PAYMENT_DECLINED.status()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void customMessageIsKept() {
        ApiError body = handler.handleApi(
                new ApiException(ErrorCode.POINTS_LIMIT_EXCEEDED, "You can redeem at most 545 points on this order."))
                .getBody();
        assertThat(body).isNotNull();
        assertThat(body.message()).isEqualTo("You can redeem at most 545 points on this order.");
    }

    @Test
    void validationFailureCarriesFieldErrors() {
        List<ApiError.FieldError> fields = List.of(new ApiError.FieldError("phone", "Phone must be exactly 10 digits."));
        ResponseEntity<ApiError> response = handler.handleApi(new ApiException(ErrorCode.VALIDATION_FAILED, fields));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors()).containsExactlyElementsOf(fields);
    }

    @Test
    void constraintViolationsBecomeValidationFailed() {
        ResponseEntity<ApiError> response = handler.handleConstraintViolation(new ConstraintViolationException(Set.of()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    @Test
    void unreadableBodyBecomesValidationFailedWithoutLeakingParserDetails() {
        ResponseEntity<ApiError> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("Unexpected character at line 1 column 7 of com.bookygeek.Secret",
                        new org.springframework.http.server.ServletServerHttpRequest(
                                new org.springframework.mock.web.MockHttpServletRequest())));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().message()).doesNotContain("Secret").doesNotContain("line 1");
    }

    @Test
    void unknownRouteOrMethodIsNotFound() {
        ResponseEntity<ApiError> response =
                handler.handleUnknownRoute(new HttpRequestMethodNotSupportedException("TRACE"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void unexpectedFailureReturnsGenericInternalErrorWithoutDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("jdbc:postgresql://db:5432/bookygeek password=" + PASSWORD));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo(ErrorCode.INTERNAL_ERROR.defaultMessage());
        assertThat(body.message()).doesNotContain("jdbc").doesNotContain(PASSWORD);
        assertThat(body.fieldErrors()).isNull();
        assertThat(body.timestamp()).isEqualTo(NOW);
    }
}
