package com.bookygeek.common.security;

import com.bookygeek.common.error.ApiError;
import com.bookygeek.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Writes the single {@link ApiError} body for security failures. The approved contract declares only 401
 * {@code UNAUTHENTICATED} for protected operations and states that 403 is not used, so both a missing or invalid
 * authentication cookie and a missing or mismatching CSRF token are answered that way. The message is generic.
 */
@Component
public class ApiSecurityErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ApiSecurityErrorWriter(JsonMapper jsonMapper, Clock clock) {
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        writeUnauthenticated(response);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writeUnauthenticated(response);
    }

    private void writeUnauthenticated(HttpServletResponse response) throws IOException {
        ErrorCode code = ErrorCode.UNAUTHENTICATED;
        ApiError body = ApiError.of(code, code.defaultMessage(), clock.instant());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getWriter().write(jsonMapper.writeValueAsString(body));
    }
}
