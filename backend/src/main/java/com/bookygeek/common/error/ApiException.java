package com.bookygeek.common.error;

import java.util.List;

/**
 * A business or validation failure that maps to one {@link ErrorCode}. The message is shown to the customer, so it
 * must be generic and must never contain secrets, SQL or stack-trace details.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<ApiError.FieldError> fieldErrors;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.fieldErrors = List.of();
    }

    public ApiException(ErrorCode code, List<ApiError.FieldError> fieldErrors) {
        super(code.defaultMessage());
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
