package com.bookygeek.common.error;

import org.springframework.http.HttpStatus;

/**
 * The machine-readable error codes of the approved {@code ApiError} schema in {@code api/openapi.yaml}.
 * The set is closed: adding a code requires a contract change and owner approval (gate G-API).
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request contains invalid values."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Please log in to continue."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "The e-mail or password is incorrect."),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "An account with this e-mail address already exists."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested item was not found."),
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "This book was not found."),
    QUANTITY_LIMIT(HttpStatus.CONFLICT, "You can add at most 10 copies of one book."),
    OUT_OF_STOCK(HttpStatus.CONFLICT, "This book is out of stock."),
    EMPTY_BASKET(HttpStatus.CONFLICT, "Your basket is empty."),
    BASKET_CHANGED(HttpStatus.CONFLICT,
            "Prices or availability in your basket have changed. Please review your basket."),
    POINTS_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "You cannot redeem that many points on this order."),
    PAYMENT_DECLINED(HttpStatus.CONFLICT,
            "Your payment was declined. Please try again or choose another payment method."),
    CANCELLATION_WINDOW_EXPIRED(HttpStatus.CONFLICT,
            "This order can no longer be cancelled because 48 hours have passed."),
    ALREADY_CANCELLED(HttpStatus.CONFLICT, "This order has already been cancelled."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "You have already reviewed this book."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    /** Generic, customer-safe message used when the thrower supplies none. */
    public String defaultMessage() {
        return defaultMessage;
    }
}
