package com.shuttleflow.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ProviderRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProviderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findProviderIdByUserId(long userId) {
        List<Long> ids = jdbcTemplate.query("SELECT provider_id FROM providers WHERE user_id = ?",
                (rs, rowNum) -> rs.getLong(1), userId);
        return ids.stream().findFirst();
    }

    public Optional<Long> findServiceProviderId(long serviceId) {
        List<Long> ids = jdbcTemplate.query("SELECT provider_id FROM services WHERE service_id = ?",
                (rs, rowNum) -> rs.getLong(1), serviceId);
        return ids.stream().findFirst();
    }

    public long createSlot(long providerId, long serviceId, LocalDateTime startTime, LocalDateTime endTime) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)
                    VALUES (?, ?, ?, ?, 'OPEN')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, providerId);
            statement.setLong(2, serviceId);
            statement.setObject(3, startTime);
            statement.setObject(4, endTime);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Slot ID was not generated.");
        }
        return key.longValue();
    }
}
