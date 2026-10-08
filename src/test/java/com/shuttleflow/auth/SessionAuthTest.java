package com.shuttleflow.auth;

import com.shuttleflow.service.ForbiddenException;
import com.shuttleflow.service.UnauthorizedException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionAuthTest {

    private final SessionAuth auth = new SessionAuth();

    @Test
    void missingIdentityIs401() {
        assertThrows(UnauthorizedException.class, () -> auth.requireAuthenticated(new MockHttpSession()));
    }

    @Test
    void customerCannotUseProviderBoundary() {
        HttpSession session = session(new UserSession(1, "customer@example.com", "Customer", "CUSTOMER", null));
        assertThrows(ForbiddenException.class, () -> auth.requireProvider(session));
    }

    @Test
    void providerCannotUseCustomerBoundary() {
        HttpSession session = session(new UserSession(1, "provider@example.com", "Provider", "PROVIDER", 9L));
        assertThrows(ForbiddenException.class, () -> auth.requireCustomer(session));
    }

    @Test
    void linkedProviderPassesProviderBoundary() {
        HttpSession session = session(new UserSession(1, "provider@example.com", "Provider", "PROVIDER", 9L));
        assertDoesNotThrow(() -> auth.requireProvider(session));
    }

    private HttpSession session(UserSession user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.ATTRIBUTE, user);
        return session;
    }
}
