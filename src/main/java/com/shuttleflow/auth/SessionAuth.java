package com.shuttleflow.auth;

import com.shuttleflow.service.ForbiddenException;
import com.shuttleflow.service.UnauthorizedException;
import org.springframework.stereotype.Component;

/**
 * Shared authorization boundary for services. The caller is the identity from a verified login
 * token (see JwtAuthFilter), or null when the request carries none.
 */
@Component
public class SessionAuth {

    public UserSession requireAuthenticated(UserSession caller) {
        if (caller == null) {
            throw new UnauthorizedException();
        }
        return caller;
    }

    public UserSession requireCustomer(UserSession caller) {
        UserSession user = requireAuthenticated(caller);
        if (!"CUSTOMER".equals(user.getRole())) {
            throw new ForbiddenException();
        }
        return user;
    }

    public UserSession requireProvider(UserSession caller) {
        UserSession user = requireAuthenticated(caller);
        if (!"PROVIDER".equals(user.getRole()) || user.getProviderId() == null) {
            throw new ForbiddenException();
        }
        return user;
    }
}
