package com.bookygeek.user.service;

import com.bookygeek.common.error.ApiError;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.security.JwtService;
import com.bookygeek.reward.service.RewardService;
import com.bookygeek.user.dto.CustomerResponse;
import com.bookygeek.user.dto.LoginRequest;
import com.bookygeek.user.dto.RegisterRequest;
import com.bookygeek.user.entity.Customer;
import com.bookygeek.user.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

/**
 * Registration, login and logout. A customer and the customer's reward account are created in one transaction. An
 * unknown e-mail runs a password comparison against a dummy hash, so the answer, the message and the time match those
 * of a wrong password. Logout bumps {@code token_version}, which invalidates every token issued before. Passwords and
 * tokens are never logged.
 */
@Service
public class AuthService {

    /** A successful login: the customer and the signed token to put in the {@code BG_AUTH} cookie. */
    public record LoginResult(CustomerResponse customer, String token) {
    }

    private final CustomerRepository customers;
    private final RewardService rewards;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(CustomerRepository customers, RewardService rewards, PasswordEncoder passwordEncoder,
            JwtService jwtService, Clock clock) {
        this.customers = customers;
        this.rewards = rewards;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        // Hash of a throw-away value, used only to spend the same time when the e-mail is unknown.
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");
    }

    /** Lower-case, trimmed form under which an e-mail is stored and compared. */
    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    List.of(new ApiError.FieldError("confirmPassword", "The passwords do not match.")));
        }
        String email = normaliseEmail(request.email());
        if (customers.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        Customer customer = new Customer(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), clock.instant());
        try {
            customer = customers.saveAndFlush(customer);
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with a concurrent registration of the same e-mail; the unique index decides.
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        rewards.createAccount(customer.getId());
        return toResponse(customer);
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        String email = normaliseEmail(request.email());
        Customer customer = customers.findByEmail(email).orElse(null);
        String hash = customer == null ? dummyHash : customer.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (customer == null || !passwordMatches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        String token = jwtService.issue(customer.getId(), customer.getTokenVersion());
        return new LoginResult(toResponse(customer), token);
    }

    /** Invalidates every token issued to the customer so far, including the one used for this request. */
    @Transactional
    public void logout(long customerId) {
        customers.bumpTokenVersion(customerId);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCurrentCustomer(long customerId) {
        return customers.findById(customerId).map(AuthService::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getFullName(), customer.getEmail());
    }
}
