package com.shuttleflow.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL access for authentication identity only. */
@Repository
public class UserRepository {

    private static final String FIND_BY_EMAIL_SQL = """
            SELECT u.user_id, u.email, u.password_hash, u.full_name, u.role, p.provider_id
            FROM users u
            LEFT JOIN providers p ON p.user_id = u.user_id
            WHERE LOWER(u.email) = LOWER(?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserAccount findByEmail(String email) {
        return jdbcTemplate.query(FIND_BY_EMAIL_SQL, rs -> {
            if (!rs.next()) {
                return null;
            }
            Long providerId = rs.getObject("provider_id", Long.class);
            return new UserAccount(rs.getLong("user_id"), rs.getString("email"),
                    rs.getString("password_hash"), rs.getString("full_name"),
                    rs.getString("role"), providerId);
        }, email);
    }

    public record UserAccount(long userId, String email, String passwordHash, String fullName,
                              String role, Long providerId) { }
}
