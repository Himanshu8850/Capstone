package com.bookygeek.catalogue.repository;

/** Helpers for building safe {@code LIKE} patterns from customer-typed search text. */
public final class LikePattern {

    private LikePattern() {
    }

    /** Makes {@code %}, {@code _} and the escape character {@code \} literal in a {@code LIKE} pattern. */
    public static String escape(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
