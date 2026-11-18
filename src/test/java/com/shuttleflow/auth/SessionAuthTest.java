package com.shuttleflow.auth;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.service.ForbiddenException;
import com.shuttleflow.service.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionAuthTest {

    private final SessionAuth auth = new SessionAuth();

    @Test
    void missingIdentityIs401() {
        assertThrows(UnauthorizedException.class, () -> auth.requireAuthenticated(null));
    }

    @Test
    void customerCannotUseProviderBoundary() {
        UserSession session = new UserSession(1, "customer@example.com", "Customer", "CUSTOMER", null);
        assertThrows(ForbiddenException.class, () -> auth.requireProvider(session));
    }

    @Test
    void providerCannotUseCustomerBoundary() {
        UserSession session = new UserSession(1, "provider@example.com", "Provider", "PROVIDER", 9L);
        assertThrows(ForbiddenException.class, () -> auth.requireCustomer(session));
    }

    @Test
    void linkedProviderPassesProviderBoundary() {
        UserSession session = new UserSession(1, "provider@example.com", "Provider", "PROVIDER", 9L);
        assertDoesNotThrow(() -> auth.requireProvider(session));
    }

}
