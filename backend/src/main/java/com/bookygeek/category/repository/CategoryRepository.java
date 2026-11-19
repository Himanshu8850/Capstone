package com.bookygeek.category.repository;

import com.bookygeek.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Every category in display order; the tree is tiny (two levels), so it is always read whole. */
    List<Category> findAllByOrderBySortOrderAscIdAsc();
}
