package com.bookygeek.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Security configuration under {@code bookygeek.*} ({@code jwt}, {@code cookie}, {@code cors}). Values come from the
 * environment (never committed); invalid values stop the application at start-up (fail closed).
 */
@ConfigurationProperties(prefix = "bookygeek")
public record SecurityProperties(@DefaultValue Jwt jwt, @DefaultValue Cookie cookie, @DefaultValue Cors cors) {

    /** Approved absolute token lifetime; the configured value may shorten it but never extend it. */
    public static final int MAX_TTL_MINUTES = 60;
    /** HS256 needs a key of at least 256 bits. */
    public static final int MIN_SECRET_BYTES = 32;

    /** HS256 signing secret and token lifetime. */
    public record Jwt(String secret, @DefaultValue("60") int ttlMinutes) {
        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "bookygeek.jwt.secret (JWT_SECRET) must be set to at least " + MIN_SECRET_BYTES + " bytes");
            }
            if (ttlMinutes < 1 || ttlMinutes > MAX_TTL_MINUTES) {
                throw new IllegalArgumentException(
                        "bookygeek.jwt.ttl-minutes must be between 1 and " + MAX_TTL_MINUTES);
            }
        }

        /** Never prints the secret. */
        @Override
        public String toString() {
            return "Jwt[secret=****, ttlMinutes=" + ttlMinutes + "]";
        }
    }

    /** {@code Secure} flag of the authentication and CSRF cookies (true except for plain-HTTP local development). */
    public record Cookie(@DefaultValue("true") boolean secure) {
    }

    /** Explicit browser origins allowed to call the API cross-origin; empty means same-origin only. */
    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
            for (String origin : allowedOrigins) {
                if (!(origin.startsWith("http://") || origin.startsWith("https://")) || origin.contains("*")) {
                    throw new IllegalArgumentException(
                            "bookygeek.cors.allowed-origins must list explicit http(s) origins, no wildcards");
                }
            }
        }
    }
}
