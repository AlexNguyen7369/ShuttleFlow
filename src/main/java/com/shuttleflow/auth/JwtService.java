package com.shuttleflow.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Signs and verifies the login token (HS256 JWT) and builds the HttpOnly cookie that carries it.
 * The token is stateless, so it cannot be revoked before it expires; the lifetime is kept short.
 */
@Component
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public static final String COOKIE_NAME = "SF_AUTH";
    static final int MIN_SECRET_BYTES = 32;

    private final byte[] secret;
    private final Duration lifetime;
    private final boolean secureCookie;

    public JwtService(@Value("${shuttleflow.jwt.secret:}") String configuredSecret,
                      @Value("${shuttleflow.jwt.ttl-minutes:120}") long ttlMinutes,
                      @Value("${shuttleflow.jwt.cookie-secure:false}") boolean secureCookie) {
        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            if (!configuredSecret.isEmpty()) {
                throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes.");
            }
            // Dev default: a random secret, so logins simply end when the server restarts.
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET not set; using a random secret (sign-ins reset on restart)");
        }
        this.secret = bytes;
        this.lifetime = Duration.ofMinutes(ttlMinutes);
        this.secureCookie = secureCookie;
    }

    public String issue(UserSession user) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(Long.toString(user.getUserId()))
                .claim("email", user.getEmail())
                .claim("name", user.getFullName())
                .claim("role", user.getRole())
                .claim("providerId", user.getProviderId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(lifetime)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the login token.", e);
        }
    }

    /** The identity in a valid, unexpired HS256 token; empty for anything else. */
    public Optional<UserSession> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            // Accept only the algorithm we sign with, so a forged "none" or other alg is rejected.
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            Long providerId = claims.getLongClaim("providerId");
            return Optional.of(new UserSession(Long.parseLong(claims.getSubject()), claims.getStringClaim("email"),
                    claims.getStringClaim("name"), claims.getStringClaim("role"), providerId));
        } catch (ParseException | JOSEException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public ResponseCookie loginCookie(String token) {
        return cookie(token, lifetime);
    }

    public ResponseCookie logoutCookie() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
