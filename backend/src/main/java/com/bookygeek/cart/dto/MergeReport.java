package com.bookygeek.cart.dto;

import java.util.List;

/** The contract's {@code BasketReport}: empty when the merge changed nothing. */
public record MergeReport(List<BasketAdjustment> adjustments) {
}
