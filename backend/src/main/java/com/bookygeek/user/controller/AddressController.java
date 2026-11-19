package com.bookygeek.user.controller;

import com.bookygeek.common.security.AuthenticatedCustomer;
import com.bookygeek.user.dto.AddressRequest;
import com.bookygeek.user.dto.AddressResponse;
import com.bookygeek.user.service.AddressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * {@code listAddresses}, {@code createAddress}, {@code updateAddress}, {@code deleteAddress} and {@code listStates}
 * exactly as in {@code api/openapi.yaml}. Addresses are always the signed-in customer's own; the states list is public.
 */
@RestController
@RequestMapping("/api/v1")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/addresses")
    public List<AddressResponse> listAddresses(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return addressService.list(customer.id());
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> createAddress(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse created = addressService.create(customer.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/addresses/" + created.id()))
                .body(created);
    }

    @PutMapping("/addresses/{id:\\d{1,18}}")
    public AddressResponse updateAddress(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable @Min(value = 1, message = "Choose a valid address.") long id,
            @Valid @RequestBody AddressRequest request) {
        return addressService.update(customer.id(), id, request);
    }

    @DeleteMapping("/addresses/{id:\\d{1,18}}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal AuthenticatedCustomer customer,
            @PathVariable @Min(value = 1, message = "Choose a valid address.") long id) {
        addressService.delete(customer.id(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reference/states")
    public List<String> listStates() {
        return addressService.listStates();
    }
}
