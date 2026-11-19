package com.bookygeek.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Passwords used by tests. Nothing is committed: the values come from the environment variables
 * {@code TEST_USER_PASSWORD} and {@code TEST_USER_WRONG_PASSWORD}, or from the git-ignored {@code .env} file in the
 * repository root (see {@code .env.example}). A real environment variable wins over the file.
 */
public final class TestCredentials {

    /** A valid account password (8-64 characters, a letter and a digit). */
    public static final String PASSWORD = require("TEST_USER_PASSWORD");

    /** A different, valid password that is not the account password. */
    public static final String WRONG_PASSWORD = require("TEST_USER_WRONG_PASSWORD");

    private TestCredentials() {
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = fromDotEnv(name);
        }
        if (value == null || value.isBlank() || "CHANGE_ME".equals(value)) {
            throw new IllegalStateException(name + " is not set. Copy .env.example to .env and set it, "
                    + "or export it as an environment variable.");
        }
        return value;
    }

    private static String fromDotEnv(String name) {
        for (String candidate : List.of("../.env", ".env")) {
            Path file = Path.of(candidate);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String trimmed = line.strip();
                    if (trimmed.startsWith(name + "=")) {
                        return trimmed.substring(name.length() + 1).strip();
                    }
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot read " + file, ex);
            }
        }
        return null;
    }
}
