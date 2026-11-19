package com.bookygeek.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Converts every failure into the single {@link ApiError} body. Clients only ever see generic messages; details of
 * unexpected failures are logged server-side and never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        ErrorCode code = ex.getCode();
        ApiError body = ex.getFieldErrors().isEmpty()
                ? ApiError.of(code, ex.getMessage(), now())
                : new ApiError(code.status().value(), code.name(), ex.getMessage(), ex.getFieldErrors(), now());
        return respond(code, body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(fe -> new ApiError.FieldError(fe.getField(), safeMessage(fe.getDefaultMessage())))
                .toList();
        return validationFailed(fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ApiError.FieldError> fields = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ApiError.FieldError(r.getMethodParameter().getParameterName() == null
                                ? "request" : r.getMethodParameter().getParameterName(),
                                safeMessage(e.getDefaultMessage()))))
                .toList();
        return validationFailed(fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(lastNode(v.getPropertyPath().toString()),
                        safeMessage(v.getMessage())))
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .toList();
        return validationFailed(fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex) {
        LOG.warn("Rejected unreadable request: {}", ex.getClass().getSimpleName());
        return validationFailed(List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return validationFailed(List.of(new ApiError.FieldError(ex.getHeaderName(), "This header is required.")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        return validationFailed(List.of(new ApiError.FieldError(ex.getParameterName(), "This value is required.")));
    }

    /** Unknown path, wrong method or unsupported media type: the contract defines no other client error code. */
    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> handleUnknownRoute(Exception ex) {
        LOG.warn("Unmatched request: {}", ex.getClass().getSimpleName());
        return respond(ErrorCode.NOT_FOUND, ApiError.of(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return respond(ErrorCode.INTERNAL_ERROR, ApiError.of(ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(), now()));
    }

    private ResponseEntity<ApiError> validationFailed(List<ApiError.FieldError> fields) {
        return respond(ErrorCode.VALIDATION_FAILED, ApiError.validation(fields, now()));
    }

    private static ResponseEntity<ApiError> respond(ErrorCode code, ApiError body) {
        return ResponseEntity.status(code.status()).body(body);
    }

    private Instant now() {
        return clock.instant();
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "Invalid value." : message;
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
