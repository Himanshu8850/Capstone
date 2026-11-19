package com.bookygeek.catalogue.dto;

/** The contract's {@code Brand}: a publisher that has at least one listed book. */
public record Brand(String name, long bookCount) {
}
