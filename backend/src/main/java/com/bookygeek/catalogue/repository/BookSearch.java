package com.bookygeek.catalogue.repository;

import com.bookygeek.catalogue.dto.BookQuery;
import com.bookygeek.catalogue.entity.Book;
import org.springframework.data.domain.Page;

/** The dynamic catalogue query: optional filters ANDed together, one sort, a fixed page size. */
public interface BookSearch {

    /** Listed books matching every non-null filter of the query, in the requested order, one page. */
    Page<Book> search(BookQuery query);
}
