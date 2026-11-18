package com.shuttleflow.controller;

import com.shuttleflow.auth.JwtAuthFilter;
import com.shuttleflow.auth.JwtService;
import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.LoginRequest;
import com.shuttleflow.dto.LoginResponse;
import com.shuttleflow.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return withLoginCookie(authService.login(request, false));
    }

    @PostMapping("/provider/login")
    public ResponseEntity<LoginResponse> providerLogin(@RequestBody LoginRequest request) {
        return withLoginCookie(authService.login(request, true));
    }

    @GetMapping("/session")
    public LoginResponse currentSession(
            @RequestAttribute(name = JwtAuthFilter.CALLER, required = false) UserSession caller) {
        return authService.currentUser(caller);
    }

    /** Stateless tokens cannot be revoked; logout tells the browser to delete the cookie. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, jwtService.logoutCookie().toString()).build();
    }

    private ResponseEntity<LoginResponse> withLoginCookie(AuthService.LoginResult result) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, jwtService.loginCookie(result.token()).toString())
                .body(result.body());
    }
}
