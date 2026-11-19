package com.bookygeek.user.controller;

import com.bookygeek.common.security.AuthCookieFactory;
import com.bookygeek.common.security.AuthenticatedCustomer;
import com.bookygeek.user.dto.CustomerResponse;
import com.bookygeek.user.dto.LoginRequest;
import com.bookygeek.user.dto.RegisterRequest;
import com.bookygeek.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * {@code register}, {@code login}, {@code logout} and {@code getCurrentCustomer} exactly as in
 * {@code api/openapi.yaml}. The controller only maps HTTP to {@link AuthService} and builds the cookies.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory cookies;

    public AuthController(AuthService authService, AuthCookieFactory cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody RegisterRequest request) {
        CustomerResponse customer = authService.register(request);
        // A customer has no resource URL of its own; the profile of the signed-in customer is /auth/me.
        return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/auth/me")).body(customer);
    }

    @PostMapping("/login")
    public ResponseEntity<CustomerResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.token()).toString())
                .body(result.customer());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        authService.logout(customer.id());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.expire().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.expireCsrf().toString())
                .build();
    }

    @GetMapping("/me")
    public CustomerResponse getCurrentCustomer(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        return authService.getCurrentCustomer(customer.id());
    }

}
