package com.shuttleflow.service;

import com.shuttleflow.auth.JwtService;
import com.shuttleflow.auth.SessionAuth;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.LoginRequest;
import com.shuttleflow.dto.LoginResponse;
import com.shuttleflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Owns credential verification and issuing the signed login token (JWT). */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionAuth sessionAuth;
    private final AiConversationService conversations;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, SessionAuth sessionAuth,
                       AiConversationService conversations, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionAuth = sessionAuth;
        this.conversations = conversations;
        this.jwtService = jwtService;
    }

    /** The login response body plus the signed token the controller puts in the HttpOnly cookie. */
    public record LoginResult(LoginResponse body, String token) { }

    public LoginResult login(LoginRequest request, boolean providerLogin) {
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

        UserSession user = new UserSession(account.userId(), account.email(), account.fullName(),
                account.role(), account.providerId());
        // A fresh login starts a fresh assistant conversation: nothing pending carries over.
        conversations.reset(account.userId());
        return new LoginResult(toResponse(user), jwtService.issue(user));
    }

    /** Returns the signed-in identity so the UI can restore its state after a reload; 401 if none. */
    public LoginResponse currentUser(UserSession caller) {
        return toResponse(sessionAuth.requireAuthenticated(caller));
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
