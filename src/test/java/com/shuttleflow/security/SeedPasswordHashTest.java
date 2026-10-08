package com.shuttleflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SeedPasswordHashTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seededAccountsUseBCryptHashesAndKnownLocalTestPasswords() {
        assertTrue(passwordEncoder.matches("court-manager-test", hashFor("court.manager@shuttleflow.com")));
        assertTrue(passwordEncoder.matches("coach-kim-test", hashFor("coach.kim@shuttleflow.com")));
        assertTrue(passwordEncoder.matches("alex-test", hashFor("alex@shuttleflow.com")));
        assertTrue(passwordEncoder.matches("jamie-test", hashFor("jamie@shuttleflow.com")));
    }

    private String hashFor(String email) {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
    }
}
