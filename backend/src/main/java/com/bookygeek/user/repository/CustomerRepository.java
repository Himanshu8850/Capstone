package com.bookygeek.user.repository;

import com.bookygeek.user.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** {@code email} must already be trimmed and lower-cased. */
    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select c.tokenVersion from Customer c where c.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") long id);

    /** Atomic increment; returns the number of rows changed (0 when the customer does not exist). */
    @Modifying(clearAutomatically = true)
    @Query("update Customer c set c.tokenVersion = c.tokenVersion + 1 where c.id = :id")
    int bumpTokenVersion(@Param("id") long id);
}
