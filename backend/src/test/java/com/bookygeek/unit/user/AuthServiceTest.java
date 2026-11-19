package com.bookygeek.unit.user;

import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.common.security.JwtService;
import com.bookygeek.common.security.PasswordConfig;
import com.bookygeek.common.security.SecurityProperties;
import com.bookygeek.reward.service.RewardService;
import com.bookygeek.user.dto.LoginRequest;
import com.bookygeek.user.dto.RegisterRequest;
import com.bookygeek.user.entity.Customer;
import com.bookygeek.user.repository.CustomerRepository;
import com.bookygeek.user.service.AuthService;
import com.bookygeek.user.service.CustomerTokenVersionLookup;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.bookygeek.testsupport.TestCredentials.PASSWORD;
import static com.bookygeek.testsupport.TestCredentials.WRONG_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Registration and login rules (T047); no Spring context and no database. */
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SECRET = "unit-test-only-secret-of-at-least-32-bytes!";
    private static final PasswordEncoder REAL_ENCODER = new PasswordConfig().passwordEncoder();
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private CustomerRepository customers;
    private RewardService rewards;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private AuthService service;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        rewards = mock(RewardService.class);
        encoder = spy(REAL_ENCODER);
        jwt = new JwtService(new SecurityProperties(new SecurityProperties.Jwt(SECRET, 60),
                new SecurityProperties.Cookie(true), new SecurityProperties.Cors(List.of())), CLOCK);
        service = new AuthService(customers, rewards, encoder, jwt, CLOCK);
        when(customers.saveAndFlush(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 42L);
            return c;
        });
    }

    private static RegisterRequest register(String name, String email, String password, String confirm) {
        return new RegisterRequest(name, email, password, confirm);
    }

    private Customer stored(String email, String password, int version) {
        Customer c = new Customer("Asha Verma", email, REAL_ENCODER.encode(password), NOW);
        ReflectionTestUtils.setField(c, "id", 7L);
        ReflectionTestUtils.setField(c, "tokenVersion", version);
        return c;
    }

    // ---- registration ---------------------------------------------------------------------------------------------

    @Test
    void emailIsTrimmedAndLowerCasedBeforeItIsStored() {
        service.register(register("  Asha Verma ", "  Asha.Verma@Example.COM ", PASSWORD, PASSWORD));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("asha.verma@example.com");
        assertThat(saved.getValue().getFullName()).isEqualTo("Asha Verma");
        verify(customers).existsByEmail("asha.verma@example.com");
    }

    @Test
    void registrationCreatesTheRewardAccountOfTheNewCustomerAndReturnsNoSecrets() {
        var response = service.register(register("Asha Verma", "asha@example.com", PASSWORD, PASSWORD));

        verify(rewards).createAccount(42L);
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.email()).isEqualTo("asha@example.com");
        assertThat(response.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void passwordIsStoredAsASaltedHashNeverInPlainText() {
        service.register(register("Asha Verma", "asha@example.com", PASSWORD, PASSWORD));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).saveAndFlush(saved.capture());
        String hash = saved.getValue().getPasswordHash();
        assertThat(hash).startsWith("{pbkdf2}").doesNotContain(PASSWORD);
        assertThat(REAL_ENCODER.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void emailThatExistsInAnyLetterCaseIsRejectedWithEmailTaken() {
        when(customers.existsByEmail("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(register("Other", "ASHA@Example.com", PASSWORD, PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_TAKEN));
        verify(customers, never()).saveAndFlush(any());
        verify(rewards, never()).createAccount(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void aConcurrentDuplicateDetectedByTheUniqueIndexIsAlsoEmailTaken() {
        when(customers.saveAndFlush(any(Customer.class))).thenThrow(new DataIntegrityViolationException("uq_customer_email"));

        assertThatThrownBy(() -> service.register(register("Asha", "asha@example.com", PASSWORD, PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_TAKEN));
        verify(rewards, never()).createAccount(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void mismatchingConfirmationIsAValidationErrorOnConfirmPassword() {
        assertThatThrownBy(() -> service.register(register("Asha", "asha@example.com", PASSWORD, WRONG_PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.getFieldErrors()).singleElement().satisfies(fe -> {
                        assertThat(fe.field()).isEqualTo("confirmPassword");
                        assertThat(fe.message()).isEqualTo("The passwords do not match.");
                    });
                });
        verify(customers, never()).saveAndFlush(any());
    }

    // ---- request validation (the contract's field rules) ----------------------------------------------------------

    private static Set<String> invalidFields(Object request) {
        return validator.validate(request).stream().map(ConstraintViolation::getPropertyPath)
                .map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void passwordMustBeEightToSixtyFourCharactersWithALetterAndADigit() {
        for (String bad : new String[] {"Short1", "abcdefgh", "12345678", "a1" + "x".repeat(63), null}) {
            assertThat(invalidFields(register("Asha", "asha@example.com", bad, bad)))
                    .as("password %s", bad).contains("password");
        }
        for (String good : new String[] {PASSWORD.substring(0, 8), "abcdefg1", "1234567a", "a1" + "x".repeat(62)}) {
            assertThat(invalidFields(register("Asha", "asha@example.com", good, good)))
                    .as("password %s", good.length()).doesNotContain("password");
        }
    }

    @Test
    void fullNameAndEmailAreRequiredAndBounded() {
        assertThat(invalidFields(register(" ", "asha@example.com", PASSWORD, PASSWORD))).contains("fullName");
        assertThat(invalidFields(register(null, "asha@example.com", PASSWORD, PASSWORD))).contains("fullName");
        assertThat(invalidFields(register("x".repeat(101), "asha@example.com", PASSWORD, PASSWORD)))
                .contains("fullName");
        for (String badEmail : new String[] {"", "  ", "plain", "a@b", "a b@c.com", "@x.com", "a@@x.com"}) {
            assertThat(invalidFields(register("Asha", badEmail, PASSWORD, PASSWORD)))
                    .as("email '%s'", badEmail).contains("email");
        }
        assertThat(invalidFields(register("Asha", "a@" + "x".repeat(250) + ".com", PASSWORD, PASSWORD)))
                .contains("email");
        assertThat(invalidFields(register("Asha", " Asha@Example.com ", PASSWORD, PASSWORD))).isEmpty();
    }

    @Test
    void loginRequestNeedsEmailAndPassword() {
        assertThat(invalidFields(new LoginRequest("", ""))).contains("email", "password");
        assertThat(invalidFields(new LoginRequest("asha@example.com", "x".repeat(65)))).contains("password");
        assertThat(invalidFields(new LoginRequest("asha@example.com", "wrong"))).isEmpty();
    }

    @Test
    void requestsNeverPrintPasswords() {
        assertThat(register("Asha", "asha@example.com", PASSWORD, PASSWORD).toString()).doesNotContain(PASSWORD);
        assertThat(new LoginRequest("asha@example.com", PASSWORD).toString()).doesNotContain(PASSWORD);
    }

    // ---- login ----------------------------------------------------------------------------------------------------

    @Test
    void loginIsCaseInsensitiveOnEmailAndIssuesATokenForThePresentVersion() {
        when(customers.findByEmail("asha@example.com")).thenReturn(Optional.of(stored("asha@example.com", PASSWORD, 3)));

        var result = service.login(new LoginRequest("  ASHA@Example.com ", PASSWORD));

        assertThat(result.customer().email()).isEqualTo("asha@example.com");
        assertThat(jwt.verify(result.token())).hasValueSatisfying(v -> {
            assertThat(v.customerId()).isEqualTo(7L);
            assertThat(v.tokenVersion()).isEqualTo(3);
        });
    }

    @Test
    void wrongPasswordIsInvalidCredentials() {
        when(customers.findByEmail("asha@example.com")).thenReturn(Optional.of(stored("asha@example.com", PASSWORD, 0)));

        assertThatThrownBy(() -> service.login(new LoginRequest("asha@example.com", WRONG_PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(ex.getMessage()).isEqualTo("The e-mail or password is incorrect.");
                });
    }

    @Test
    void unknownEmailRunsTheDummyHashComparisonAndIsIndistinguishableFromAWrongPassword() {
        when(customers.findByEmail(anyString())).thenReturn(Optional.empty());

        org.mockito.Mockito.clearInvocations(encoder);
        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", PASSWORD)))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(ex.getMessage()).isEqualTo("The e-mail or password is incorrect.");
                });
        // Same work as for a known e-mail: exactly one comparison, against a well-formed hash.
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(encoder).matches(eq(PASSWORD), hash.capture());
        assertThat(hash.getValue()).startsWith("{pbkdf2}");
    }

    // ---- logout and token version ---------------------------------------------------------------------------------

    @Test
    void logoutBumpsTheTokenVersion() {
        service.logout(7L);
        verify(customers).bumpTokenVersion(7L);
    }

    @Test
    void tokenVersionLookupReturnsTheStoredVersionOrEmptyForAnUnknownCustomer() {
        when(customers.findTokenVersionById(7L)).thenReturn(Optional.of(4));
        when(customers.findTokenVersionById(99L)).thenReturn(Optional.empty());

        CustomerTokenVersionLookup lookup = new CustomerTokenVersionLookup(customers);
        assertThat(lookup.currentTokenVersion(7L)).hasValue(4);
        assertThat(lookup.currentTokenVersion(99L)).isEmpty();
    }

    @Test
    void currentCustomerOfAMissingAccountIsUnauthenticated() {
        when(customers.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCurrentCustomer(99L))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
}
