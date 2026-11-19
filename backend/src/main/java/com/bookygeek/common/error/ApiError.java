package com.bookygeek.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error body of every 4xx and 5xx response; mirrors the {@code ApiError} schema in
 * {@code api/openapi.yaml}. {@code fieldErrors} is present only for {@code VALIDATION_FAILED}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String code, String message, List<FieldError> fieldErrors, Instant timestamp) {

    /** One invalid input field. */
    public record FieldError(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, Instant timestamp) {
        return new ApiError(code.status().value(), code.name(), message, null, timestamp);
    }

    public static ApiError validation(List<FieldError> fieldErrors, Instant timestamp) {
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return new ApiError(code.status().value(), code.name(), code.defaultMessage(),
                List.copyOf(fieldErrors), timestamp);
    }
}
