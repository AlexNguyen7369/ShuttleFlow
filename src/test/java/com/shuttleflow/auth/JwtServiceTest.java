package com.shuttleflow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The login token: what it accepts, what it rejects, and the cookie that carries it. */
class JwtServiceTest {
    private static final String SECRET = "test-secret-that-is-at-least-32-bytes!";
    private static final UserSession ALEX = new UserSession(3, "alex@shuttleflow.com", "Alex Nguyen", "CUSTOMER", null);
    private static final UserSession COURT = new UserSession(1, "court@shuttleflow.com", "Court Manager", "PROVIDER", 1L);

    private final JwtService jwt = new JwtService(SECRET, 120, false);

    @Test
    void aSignedTokenRoundTripsTheIdentity() {
        UserSession customer = jwt.verify(jwt.issue(ALEX)).orElseThrow();
        assertThat(customer.getUserId()).isEqualTo(3);
        assertThat(customer.getRole()).isEqualTo("CUSTOMER");
        assertThat(customer.getProviderId()).isNull();

        UserSession provider = jwt.verify(jwt.issue(COURT)).orElseThrow();
        assertThat(provider.getProviderId()).isEqualTo(1L);
        assertThat(provider.getFullName()).isEqualTo("Court Manager");
    }

    @Test
    void anExpiredTokenIsRejected() {
        JwtService expired = new JwtService(SECRET, -1, false);
        assertThat(jwt.verify(expired.issue(ALEX))).isEmpty();
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService("a-completely-different-secret-of-32b!", 120, false);
        assertThat(jwt.verify(other.issue(ALEX))).isEmpty();
    }

    @Test
    void anUnsignedAlgNoneTokenIsRejected() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64.encodeToString(("{\"sub\":\"3\",\"role\":\"PROVIDER\",\"providerId\":1,\"exp\":"
                + (System.currentTimeMillis() / 1000 + 3600) + "}").getBytes(StandardCharsets.UTF_8));
        assertThat(jwt.verify(header + "." + claims + ".")).isEmpty();
    }

    @Test
    void aChangedClaimBreaksTheSignature() {
        String[] parts = jwt.issue(ALEX).split("\\.");
        String forgedClaims = Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                        .replace("CUSTOMER", "PROVIDER").getBytes(StandardCharsets.UTF_8));
        assertThat(jwt.verify(parts[0] + "." + forgedClaims + "." + parts[2])).isEmpty();
    }

    @Test
    void aConfiguredSecretShorterThan32BytesIsRefused() {
        assertThatThrownBy(() -> new JwtService("too-short", 120, false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCookieIsHttpOnlyAndSameSiteLaxAndLogoutExpiresIt() {
        ResponseCookie login = jwt.loginCookie("token");
        assertThat(login.isHttpOnly()).isTrue();
        assertThat(login.getSameSite()).isEqualTo("Lax");
        assertThat(login.getPath()).isEqualTo("/");
        assertThat(login.getMaxAge().toMinutes()).isEqualTo(120);

        ResponseCookie logout = jwt.logoutCookie();
        assertThat(logout.getMaxAge().isZero()).isTrue();
        assertThat(logout.getValue()).isEmpty();
    }
}
