package com.bookygeek.catalogue.dto;

import java.util.List;

/** The contract's {@code FilterOptions}. */
public record FilterOptions(List<String> languages, List<BookFormat> formats, List<PriceBand> priceBands) {

    /** A labelled price preset; a {@code null} bound is open-ended, otherwise a two-decimal string. */
    public record PriceBand(String label, String minPrice, String maxPrice) {
    }
}
