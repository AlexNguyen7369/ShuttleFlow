package com.shuttleflow.repository;

import com.shuttleflow.dto.SlotDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SlotRepository {

    private static final String FIND_AVAILABLE_SQL = """
            SELECT s.slot_id, p.name AS provider_name, sv.name AS service_name,
                   s.start_time, s.end_time, sv.price
            FROM availability_slots s
            JOIN providers p ON p.provider_id = s.provider_id
            JOIN services sv ON sv.service_id = s.service_id
            WHERE s.status = 'OPEN'
            ORDER BY s.start_time
            """;

    private static final String COUNT_OPEN_SQL =
            "SELECT COUNT(*) FROM availability_slots WHERE status = 'OPEN'";

    private final JdbcTemplate jdbcTemplate;

    public SlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SlotDto> findAvailable() {
        return jdbcTemplate.query(FIND_AVAILABLE_SQL, (rs, rowNum) -> new SlotDto(
                rs.getLong("slot_id"),
                rs.getString("provider_name"),
                rs.getString("service_name"),
                rs.getTimestamp("start_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getBigDecimal("price")
        ));
    }

    public int countOpen() {
        Integer count = jdbcTemplate.queryForObject(COUNT_OPEN_SQL, Integer.class);
        return count == null ? 0 : count;
    }
}
