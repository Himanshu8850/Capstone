package com.bookygeek.user.repository;

import com.bookygeek.user.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** The customer's addresses, oldest first. */
    List<Address> findByCustomerIdOrderById(long customerId);

    /** An address only when it belongs to the customer; another customer's id looks like a missing one. */
    Optional<Address> findByIdAndCustomerId(long id, long customerId);
}
