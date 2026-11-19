package com.bookygeek.common.security;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the authentication cookie: {@code BG_AUTH}, HttpOnly, SameSite=Strict, {@code Path=/api}, {@code Secure}
 * from configuration. The browser sends it automatically; the SPA never reads it.
 */
@Component
public class AuthCookieFactory {

    public static final String COOKIE_NAME = "BG_AUTH";
    static final String COOKIE_PATH = "/api";

    private final SecurityProperties properties;
    private final JwtService jwtService;

    public AuthCookieFactory(SecurityProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    /** Cookie carrying a freshly issued token; its lifetime equals the token lifetime. */
    public ResponseCookie issue(String token) {
        return base(token).maxAge(jwtService.lifetime()).build();
    }

    /** Cookie that removes the authentication cookie in the browser (used by logout). */
    public ResponseCookie expire() {
        return base("").maxAge(0).build();
    }

    /** Cookie that removes the readable {@code XSRF-TOKEN} cookie (scoped to {@code /}, like the one issued). */
    public ResponseCookie expireCsrf() {
        return ResponseCookie.from("XSRF-TOKEN", "").path("/").maxAge(0).sameSite("Strict")
                .secure(properties.cookie().secure()).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
