package com.shuttleflow.service;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.LoginRequest;
import com.shuttleflow.dto.LoginResponse;
import com.shuttleflow.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Owns credential verification and creation of the server-side session. */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request, HttpSession session, boolean providerLogin) {
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

        UserSession userSession = new UserSession(account.userId(), account.email(), account.fullName(),
                account.role(), account.providerId());
        session.setAttribute(UserSession.ATTRIBUTE, userSession);
        return new LoginResponse(account.userId(), account.email(), account.fullName(), account.role(),
                account.providerId());
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
