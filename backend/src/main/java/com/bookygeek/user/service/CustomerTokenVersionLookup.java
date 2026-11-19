package com.bookygeek.user.service;

import com.bookygeek.common.security.TokenVersionLookup;
import com.bookygeek.user.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.OptionalInt;

/**
 * Tells the security layer each customer's current {@code token_version}. The JWT filter accepts a token only while
 * its version equals this value, so logout (which bumps it) invalidates every token issued before. It is a separate
 * bean so the security layer depends on the small port only, never on {@link AuthService}.
 */
@Service
public class CustomerTokenVersionLookup implements TokenVersionLookup {

    private final CustomerRepository customers;

    public CustomerTokenVersionLookup(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public OptionalInt currentTokenVersion(long customerId) {
        return customers.findTokenVersionById(customerId).map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }
}
