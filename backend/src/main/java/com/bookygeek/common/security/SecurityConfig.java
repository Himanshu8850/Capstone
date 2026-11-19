package com.bookygeek.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Spring Security 7 configuration: stateless, JWT in the HttpOnly {@code BG_AUTH} cookie, CSRF through the readable
 * {@code XSRF-TOKEN} cookie echoed in {@code X-XSRF-TOKEN}, explicit CORS origins, security headers. The public
 * operations are exactly those of {@code api/openapi.yaml} that declare {@code security: []} or inherit no security
 * requirement (catalogue, reference data, register, login, basket preview); every other path needs authentication.
 *
 * <p>The approved contract uses only 401 {@code UNAUTHENTICATED} for security failures (403 is not used), so a missing
 * or invalid cookie and a missing or mismatching CSRF token are both answered by {@link ApiSecurityErrorWriter}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String API = "/api/v1";
    static final String CSRF_COOKIE = "XSRF-TOKEN";
    static final String CSRF_HEADER = "X-XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties,
            JwtService jwtService, ObjectProvider<TokenVersionLookup> tokenVersionLookup,
            ApiSecurityErrorWriter errorWriter) throws Exception {

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieName(CSRF_COOKIE);
        csrfRepository.setHeaderName(CSRF_HEADER);
        // The SPA reads this cookie from any page path, so it must be scoped to "/" (the auth cookie is /api only).
        csrfRepository.setCookieCustomizer(cookie -> cookie.path("/")
                .secure(properties.cookie().secure()).sameSite("Strict"));

        RequestMatcher publicPost = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, API + "/auth/register");
        RequestMatcher publicLogin = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, API + "/auth/login");
        RequestMatcher publicPreview = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, API + "/basket/preview");
        RequestMatcher csrfRequired = request -> !SAFE_METHODS.contains(request.getMethod())
                && !publicPost.matches(request) && !publicLogin.matches(request) && !publicPreview.matches(request);

        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // Stateless JWT: there is no session to fix, and the default strategy would expire the
                        // XSRF-TOKEN cookie on every authenticated request, breaking the next state-changing call.
                        .sessionAuthenticationStrategy((authentication, request, response) -> { })
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(csrfRequired))
                .exceptionHandling(e -> e.authenticationEntryPoint(errorWriter).accessDeniedHandler(errorWriter))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, API + "/auth/register", API + "/auth/login",
                                API + "/basket/preview").permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/reference/states", API + "/categories",
                                API + "/brands", API + "/books", API + "/books/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, tokenVersionLookup),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    /** Allows only the explicitly configured origins; with none configured the API is same-origin only. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.cors().allowedOrigins();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Content-Type", CSRF_HEADER, "Idempotency-Key"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            source.registerCorsConfiguration(API + "/**", config);
        }
        return source;
    }

    /** Touches the deferred CSRF token so that the {@code XSRF-TOKEN} cookie is set on responses. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
