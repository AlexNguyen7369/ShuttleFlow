package com.shuttleflow.service;

import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.LoginRequest;
import com.shuttleflow.dto.LoginResponse;
import com.shuttleflow.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Owns credential verification and creation of the server-side session. */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionAuth sessionAuth;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionAuth sessionAuth) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionAuth = sessionAuth;
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, boolean providerLogin) {
        validateRequest(request);
        UserRepository.UserAccount account = userRepository.findByEmail(request.getEmail().trim());

        // Keep lookup and password failures indistinguishable to callers.
        if (account == null || !passwordEncoder.matches(request.getPassword(), account.passwordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        if (providerLogin && !"PROVIDER".equals(account.role())) {
            throw new ForbiddenException();
        }
        if (providerLogin && account.providerId() == null) {
            throw new ForbiddenException();
        }

        // Issue a brand-new session on login so a pre-login session ID can never be reused (session fixation).
        HttpSession previous = httpRequest.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        UserSession userSession = new UserSession(account.userId(), account.email(), account.fullName(),
                account.role(), account.providerId());
        httpRequest.getSession(true).setAttribute(UserSession.ATTRIBUTE, userSession);
        return toResponse(userSession);
    }

    /** Returns the signed-in identity so the UI can restore its state after a reload; 401 if none. */
    public LoginResponse currentUser(HttpSession session) {
        return toResponse(sessionAuth.requireAuthenticated(session));
    }

    private LoginResponse toResponse(UserSession user) {
        return new LoginResponse(user.getUserId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getProviderId());
    }

    private void validateRequest(LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidRequestException("email and password are required.");
        }
        if (request.getEmail().length() > 255 || request.getPassword().length() > 255) {
            throw new InvalidRequestException("email and password are invalid.");
        }
    }
}
