package com.shuttleflow.auth;

import com.shuttleflow.service.ForbiddenException;
import com.shuttleflow.service.UnauthorizedException;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/** Shared authentication boundary for controllers and services. */
@Component
public class SessionAuth {

    public UserSession requireAuthenticated(HttpSession session) {
        if (session == null) {
            throw new UnauthorizedException();
        }
        Object identity = session.getAttribute(UserSession.ATTRIBUTE);
        if (!(identity instanceof UserSession userSession)) {
            throw new UnauthorizedException();
        }
        return userSession;
    }

    public UserSession requireCustomer(HttpSession session) {
        UserSession user = requireAuthenticated(session);
        if (!"CUSTOMER".equals(user.getRole())) {
            throw new ForbiddenException();
        }
        return user;
    }

    public UserSession requireProvider(HttpSession session) {
        UserSession user = requireAuthenticated(session);
        if (!"PROVIDER".equals(user.getRole()) || user.getProviderId() == null) {
            throw new ForbiddenException();
        }
        return user;
    }
}
