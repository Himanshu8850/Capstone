package com.bookygeek.catalogue.repository;

import java.math.BigDecimal;

/** Columns read under {@code SELECT … FOR UPDATE} when an order is placed (see {@code BookRepository}). */
public interface PurchaseRow {

    Long getId();

    String getTitle();

    String getFormat();

    BigDecimal getPrice();

    /** Stock quantity; {@code null} for an eBook. */
    Integer getStock();
}
