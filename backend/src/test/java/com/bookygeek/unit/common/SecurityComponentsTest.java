package com.bookygeek.unit.common;

import com.bookygeek.common.security.AuthCookieFactory;
import com.bookygeek.common.security.JwtService;
import com.bookygeek.common.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityComponentsTest {

    private static final String SECRET = "unit-test-only-secret-of-at-least-32-bytes!";
    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    private static SecurityProperties props(String secret, int ttl, boolean secure) {
        return new SecurityProperties(new SecurityProperties.Jwt(secret, ttl),
                new SecurityProperties.Cookie(secure), new SecurityProperties.Cors(List.of()));
    }

    private static JwtService service(Clock clock) {
        return new JwtService(props(SECRET, 60, true), clock);
    }

    // ---- JwtService -------------------------------------------------------------------------------------------

    @Test
    void issuedTokenVerifiesAndCarriesCustomerIdAndTokenVersion() {
        JwtService jwt = service(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = jwt.issue(42L, 3);
        assertThat(jwt.verify(token)).hasValueSatisfying(v -> {
            assertThat(v.customerId()).isEqualTo(42L);
            assertThat(v.tokenVersion()).isEqualTo(3);
        });
    }

    @Test
    void tokenIsAcceptedJustBeforeAndRejectedAtExpiry() {
        String token = service(Clock.fixed(NOW, ZoneOffset.UTC)).issue(1L, 0);
        assertThat(service(Clock.fixed(NOW.plus(Duration.ofMinutes(59)).plusSeconds(59), ZoneOffset.UTC))
                .verify(token)).isPresent();
        assertThat(service(Clock.fixed(NOW.plus(Duration.ofMinutes(60)), ZoneOffset.UTC)).verify(token)).isEmpty();
        assertThat(service(Clock.fixed(NOW.plus(Duration.ofDays(2)), ZoneOffset.UTC)).verify(token)).isEmpty();
    }

    @Test
    void lifetimeIsExactlyTheConfiguredAbsoluteTtl() {
        JwtService jwt = new JwtService(props(SECRET, 15, true), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(jwt.lifetime()).isEqualTo(Duration.ofMinutes(15));
        String token = jwt.issue(1L, 0);
        assertThat(new JwtService(props(SECRET, 15, true), Clock.fixed(NOW.plus(Duration.ofMinutes(14)), ZoneOffset.UTC))
                .verify(token)).isPresent();
        assertThat(new JwtService(props(SECRET, 15, true), Clock.fixed(NOW.plus(Duration.ofMinutes(15)), ZoneOffset.UTC))
                .verify(token)).isEmpty();
    }

    @Test
    void tamperedWrongKeyGarbageAndEmptyTokensAreRejected() {
        JwtService jwt = service(Clock.fixed(NOW, ZoneOffset.UTC));
        String token = jwt.issue(1L, 0);
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThat(jwt.verify(tampered)).isEmpty();
        assertThat(new JwtService(props("another-secret-that-is-long-enough-123456", 60, true),
                Clock.fixed(NOW, ZoneOffset.UTC)).verify(token)).isEmpty();
        assertThat(jwt.verify("not.a.jwt")).isEmpty();
        assertThat(jwt.verify("")).isEmpty();
        assertThat(jwt.verify(null)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerOrWithoutVersionIsRejected() {
        SecretKeySpec key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet wrongIssuer = JwtClaimsSet.builder().issuer("someone-else").subject("1")
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(600)).claim("ver", 0).build();
        JwtClaimsSet noVersion = JwtClaimsSet.builder().issuer("bookygeek").subject("1")
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(600)).build();
        JwtClaimsSet noExpiry = JwtClaimsSet.builder().issuer("bookygeek").subject("1")
                .issuedAt(NOW).claim("ver", 0).build();
        JwtService jwt = service(Clock.fixed(NOW, ZoneOffset.UTC));
        for (JwtClaimsSet claims : List.of(wrongIssuer, noVersion, noExpiry)) {
            String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
            assertThat(jwt.verify(token)).isEmpty();
        }
    }

    @Test
    void tokenPayloadHoldsNoPersonalOrSecretData() {
        String token = service(Clock.fixed(NOW, ZoneOffset.UTC)).issue(7L, 1);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"7\"").contains("\"ver\":1")
                .doesNotContain("@").doesNotContain("password").doesNotContain(SECRET);
    }

    // ---- SecurityProperties (fail closed) -----------------------------------------------------------------------

    @Test
    void missingShortOrOverlongJwtSettingsStopStartUp() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(null, 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Jwt("too-short", 60)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Jwt(SECRET, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Jwt(SECRET, 61)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SecurityProperties.Jwt(SECRET, 60).toString()).doesNotContain(SECRET).contains("****");
    }

    @Test
    void corsOriginsMustBeExplicit() {
        assertThatThrownBy(() -> new SecurityProperties.Cors(List.of("*"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Cors(List.of("https://*.example.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Cors(List.of("localhost:3000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new SecurityProperties.Cors(List.of(" http://127.0.0.1:3000 ", "")).allowedOrigins())
                .containsExactly("http://127.0.0.1:3000");
        assertThat(new SecurityProperties.Cors(null).allowedOrigins()).isEmpty();
    }

    // ---- AuthCookieFactory -------------------------------------------------------------------------------------

    @Test
    void authCookieHasTheApprovedFlags() {
        JwtService jwt = service(Clock.fixed(NOW, ZoneOffset.UTC));
        AuthCookieFactory factory = new AuthCookieFactory(props(SECRET, 60, true), jwt);
        ResponseCookie cookie = factory.issue("token-value");
        assertThat(cookie.getName()).isEqualTo("BG_AUTH");
        assertThat(cookie.getValue()).isEqualTo("token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void secureFlagFollowsConfigurationAndExpireClearsTheCookie() {
        JwtService jwt = service(Clock.fixed(NOW, ZoneOffset.UTC));
        AuthCookieFactory local = new AuthCookieFactory(props(SECRET, 60, false), jwt);
        assertThat(local.issue("x").isSecure()).isFalse();
        ResponseCookie expired = local.expire();
        assertThat(expired.getValue()).isEmpty();
        assertThat(expired.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(expired.isHttpOnly()).isTrue();
        assertThat(expired.getPath()).isEqualTo("/api");
        assertThat(expired.getSameSite()).isEqualTo("Strict");
    }

    // ---- PasswordConfig ----------------------------------------------------------------------------------------

    @Test
    void passwordsAreHashedWithSaltedPbkdf2AndNeverStoredInPlainText() {
        PasswordEncoder encoder = new com.bookygeek.common.security.PasswordConfig().passwordEncoder();
        String hash = encoder.encode("Passw0rd-demo");
        assertThat(hash).startsWith("{pbkdf2}").doesNotContain("Passw0rd-demo");
        assertThat(hash.length()).isLessThanOrEqualTo(255);
        assertThat(encoder.matches("Passw0rd-demo", hash)).isTrue();
        assertThat(encoder.matches("passw0rd-demo", hash)).isFalse();
        assertThat(encoder.encode("Passw0rd-demo")).isNotEqualTo(hash);
    }
}
