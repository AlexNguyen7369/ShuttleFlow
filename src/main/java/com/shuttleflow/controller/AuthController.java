package com.shuttleflow.controller;

import com.shuttleflow.dto.LoginRequest;
import com.shuttleflow.dto.LoginResponse;
import com.shuttleflow.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpSession session) {
        return authService.login(request, session, false);
    }

    @PostMapping("/provider/login")
    public LoginResponse providerLogin(@RequestBody LoginRequest request, HttpSession session) {
        return authService.login(request, session, true);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
