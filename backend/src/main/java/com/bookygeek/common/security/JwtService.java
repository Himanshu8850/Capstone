package com.bookygeek.common.security;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Issues and verifies the HS256 authentication token (absolute lifetime, no refresh token). The token carries only the
 * customer id ({@code sub}) and the customer's {@code token_version} ({@code ver}); logout bumps the version so every
 * issued token stops working. The signing secret comes from {@code JWT_SECRET} and is never logged.
 */
@Service
public class JwtService {

    static final String ISSUER = "bookygeek";
    static final String VERSION_CLAIM = "ver";

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;
    private final Duration lifetime;

    public JwtService(SecurityProperties properties, Clock clock) {
        SecretKey key = new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        List<OAuth2TokenValidator<Jwt>> validators = List.of(timestamps, new JwtIssuerValidator(ISSUER));
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        this.clock = clock;
        this.lifetime = Duration.ofMinutes(properties.jwt().ttlMinutes());
    }

    /** A verified token: who it was issued to and which {@code token_version} it was issued for. */
    public record VerifiedToken(long customerId, int tokenVersion) {
    }

    public Duration lifetime() {
        return lifetime;
    }

    /** Signs a new token for the customer, valid for {@link #lifetime()} from now. */
    public String issue(long customerId, int tokenVersion) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(Long.toString(customerId))
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .claim(VERSION_CLAIM, tokenVersion)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Verifies signature, algorithm, issuer and expiry. Any problem yields an empty result; the reason is deliberately
     * not exposed and the token is never logged.
     */
    public Optional<VerifiedToken> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            Number version = jwt.getClaim(VERSION_CLAIM);
            if (jwt.getSubject() == null || version == null) {
                return Optional.empty();
            }
            // The library still accepts a token at the exact expiry instant; the lifetime is absolute, so reject it.
            Instant expiresAt = jwt.getExpiresAt();
            if (expiresAt == null || !clock.instant().isBefore(expiresAt)) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedToken(Long.parseLong(jwt.getSubject()), version.intValue()));
        } catch (JwtException | IllegalArgumentException | ClassCastException ex) {
            return Optional.empty();
        }
    }
}
