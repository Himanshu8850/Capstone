package com.bookygeek.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;

import java.util.Map;

/**
 * Password hashing: a {@link DelegatingPasswordEncoder} whose default (and only) scheme is salted PBKDF2-HMAC-SHA256
 * with 600,000 iterations (OWASP guidance). Stored hashes carry the {@code {pbkdf2}} prefix, so the scheme can be
 * upgraded later without invalidating existing passwords. Passwords are never stored or logged in plain text.
 */
@Configuration
public class PasswordConfig {

    static final String ID = "pbkdf2";
    static final int SALT_BYTES = 16;
    static final int ITERATIONS = 600_000;

    @Bean
    public PasswordEncoder passwordEncoder() {
        Pbkdf2PasswordEncoder pbkdf2 =
                new Pbkdf2PasswordEncoder("", SALT_BYTES, ITERATIONS, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        pbkdf2.setEncodeHashAsBase64(true);
        return new DelegatingPasswordEncoder(ID, Map.of(ID, pbkdf2));
    }
}
