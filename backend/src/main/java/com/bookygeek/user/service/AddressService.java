package com.bookygeek.user.service;

import com.bookygeek.common.error.ApiError;
import com.bookygeek.common.error.ApiException;
import com.bookygeek.common.error.ErrorCode;
import com.bookygeek.user.dto.AddressRequest;
import com.bookygeek.user.dto.AddressResponse;
import com.bookygeek.user.entity.Address;
import com.bookygeek.user.repository.AddressRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * A customer's saved delivery addresses (FR-CHK-02..04) and the states reference list. Every lookup is scoped to the
 * signed-in customer, so another customer's address is answered as {@code NOT_FOUND}. When the country is India the
 * state must be one of the 36 listed states and union territories; for any other country the state is free text.
 * The Order module uses {@link #findOwned} and {@link #validated} to obtain the address it copies into an order.
 */
@Service
@Transactional(readOnly = true)
public class AddressService {

    private static final String STATES_RESOURCE = "reference/states.json";

    private final AddressRepository addresses;
    private final Clock clock;
    private final List<String> states;

    public AddressService(AddressRepository addresses, Clock clock) {
        this.addresses = addresses;
        this.clock = clock;
        this.states = loadStates();
    }

    /** The 28 states and 8 union territories in alphabetical order. */
    public List<String> listStates() {
        return states;
    }

    public List<AddressResponse> list(long customerId) {
        return addresses.findByCustomerIdOrderById(customerId).stream().map(AddressService::toResponse).toList();
    }

    @Transactional
    public AddressResponse create(long customerId, AddressRequest request) {
        AddressRequest clean = validated(request);
        Address saved = addresses.save(new Address(customerId, clean.fullName(), clean.phone(), clean.addressLine1(),
                clean.addressLine2(), clean.city(), clean.state(), clean.pinCode(), clean.country(), clock.instant()));
        return toResponse(saved);
    }

    @Transactional
    public AddressResponse update(long customerId, long id, AddressRequest request) {
        Address address = owned(customerId, id);
        AddressRequest clean = validated(request);
        address.replaceWith(clean.fullName(), clean.phone(), clean.addressLine1(), clean.addressLine2(),
                clean.city(), clean.state(), clean.pinCode(), clean.country(), clock.instant());
        return toResponse(addresses.save(address));
    }

    @Transactional
    public void delete(long customerId, long id) {
        addresses.delete(owned(customerId, id));
        addresses.flush();
    }

    /** A saved address of this customer, as the snapshot an order copies; empty when it is not theirs or not there. */
    public Optional<AddressResponse> findOwned(long customerId, long id) {
        return addresses.findByIdAndCustomerId(id, customerId).map(AddressService::toResponse);
    }

    /**
     * Trims the entered values, applies the India default and checks the state rule. The result is what is stored
     * (saved address) or copied into the order (address entered at checkout).
     */
    public AddressRequest validated(AddressRequest request) {
        String country = request.countryOrDefault();
        String state = request.state().trim();
        if (AddressRequest.DEFAULT_COUNTRY.equalsIgnoreCase(country)) {
            country = AddressRequest.DEFAULT_COUNTRY;
            String entered = state;
            state = states.stream().filter(s -> s.equalsIgnoreCase(entered)).findFirst().orElse(null);
            if (state == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        List.of(new ApiError.FieldError("state", "Choose one of the listed states.")));
            }
        }
        String line2 = request.addressLine2() == null || request.addressLine2().isBlank()
                ? null : request.addressLine2().trim();
        return new AddressRequest(request.fullName().trim(), request.phone(), request.addressLine1().trim(), line2,
                request.city().trim(), state, request.pinCode(), country);
    }

    private Address owned(long customerId, long id) {
        return addresses.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static AddressResponse toResponse(Address a) {
        return new AddressResponse(a.getId(), a.getFullName(), a.getPhone(), a.getAddressLine1(), a.getAddressLine2(),
                a.getCity(), a.getState(), a.getPinCode(), a.getCountry());
    }

    private static List<String> loadStates() {
        try (InputStream in = new ClassPathResource(STATES_RESOURCE).getInputStream()) {
            String[] names = JsonMapper.builder().build().readValue(in, String[].class);
            Set<String> sorted = new TreeSet<>(Arrays.asList(names));
            return List.copyOf(sorted);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + STATES_RESOURCE, ex);
        }
    }
}
