package com.shuttleflow.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the login cookie on every request and, if its token is valid, exposes the caller's identity
 * as a request attribute. It never rejects a request: the 401/403 decisions stay in SessionAuth.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String CALLER = "shuttleflow.caller";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (JwtService.COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isEmpty()) {
                    jwtService.verify(cookie.getValue()).ifPresent(user -> request.setAttribute(CALLER, user));
                }
            }
        }
        chain.doFilter(request, response);
    }
}
