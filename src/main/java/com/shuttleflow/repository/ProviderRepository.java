package com.shuttleflow.repository;

import com.shuttleflow.dto.ServiceDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ProviderRepository {

    private static final String FIND_SERVICE_PROVIDER_SQL = "SELECT provider_id FROM services WHERE service_id = ?";

    private static final String INSERT_SLOT_SQL = """
            INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)
            VALUES (?, ?, ?, ?, 'OPEN')
            """;

    private static final String FIND_SERVICES_SQL = """
            SELECT service_id, name, duration_min, max_players, price
            FROM services
            WHERE provider_id = ?
            ORDER BY service_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProviderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findServiceProviderId(long serviceId) {
        return jdbcTemplate.query(FIND_SERVICE_PROVIDER_SQL, (rs, rowNum) -> rs.getLong(1), serviceId)
                .stream().findFirst();
    }

    public List<ServiceDto> findServices(long providerId) {
        return jdbcTemplate.query(FIND_SERVICES_SQL, (rs, rowNum) -> new ServiceDto(
                rs.getLong("service_id"),
                rs.getString("name"),
                rs.getInt("duration_min"),
                rs.getInt("max_players"),
                rs.getBigDecimal("price")), providerId);
    }

    /** Inserts an OPEN slot; UNIQUE (provider_id, start_time) rejects duplicates. */
    public long createSlot(long providerId, long serviceId, LocalDateTime startTime, LocalDateTime endTime) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            // Naming the key column keeps PostgreSQL from returning every column as a "generated key".
            PreparedStatement statement = connection.prepareStatement(INSERT_SLOT_SQL, new String[] {"slot_id"});
            statement.setLong(1, providerId);
            statement.setLong(2, serviceId);
            statement.setTimestamp(3, Timestamp.valueOf(startTime));
            statement.setTimestamp(4, Timestamp.valueOf(endTime));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Slot ID was not generated.");
        }
        return key.longValue();
    }
}
