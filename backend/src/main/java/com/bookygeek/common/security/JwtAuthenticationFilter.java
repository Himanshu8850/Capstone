package com.bookygeek.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.OptionalInt;

/**
 * Authenticates a request from the {@code BG_AUTH} cookie. A token is accepted only when its signature, issuer and
 * expiry are valid and its {@code ver} equals the customer's current {@code token_version}. Anything else leaves the
 * request unauthenticated, so protected paths answer 401 {@code UNAUTHENTICATED}. If no {@link TokenVersionLookup}
 * is available, no token is accepted (fail closed).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";

    private final JwtService jwtService;
    private final ObjectProvider<TokenVersionLookup> tokenVersionLookup;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectProvider<TokenVersionLookup> tokenVersionLookup) {
        this.jwtService = jwtService;
        this.tokenVersionLookup = tokenVersionLookup;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = tokenFrom(request);
        if (token != null) {
            authenticate(token);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        TokenVersionLookup lookup = tokenVersionLookup.getIfAvailable();
        if (lookup == null) {
            return;
        }
        jwtService.verify(token).ifPresent(verified -> {
            OptionalInt current = lookup.currentTokenVersion(verified.customerId());
            if (current.isPresent() && current.getAsInt() == verified.tokenVersion()) {
                UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedCustomer(verified.customerId()), null,
                        AuthorityUtils.createAuthorityList(ROLE_CUSTOMER));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
        });
    }

    private static String tokenFrom(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieFactory.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
