package com.bookygeek.catalogue.repository;

import com.bookygeek.catalogue.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** The reviews of one book, newest first (id breaks ties). */
    List<Review> findByBookIdOrderByCreatedAtDescIdDesc(long bookId);

    boolean existsByBookIdAndCustomerId(long bookId, long customerId);
}
