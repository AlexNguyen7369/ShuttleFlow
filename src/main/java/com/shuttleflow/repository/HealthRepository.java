package com.shuttleflow.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HealthRepository {

    private static final String PING_SQL = "SELECT 1";

    private final JdbcTemplate jdbcTemplate;

    public HealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Round-trips to the database; throws DataAccessException when it is unreachable. */
    public void ping() {
        jdbcTemplate.queryForObject(PING_SQL, Integer.class);
    }
}
