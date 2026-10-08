package com.shuttleflow.repository;

import com.shuttleflow.dto.SlotDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class SlotRepository {

    private static final String SLOT_DTO_SELECT_SQL = """
            SELECT s.slot_id, p.name AS provider_name, sv.name AS service_name,
                   s.start_time, s.end_time, sv.price
            FROM availability_slots s
            JOIN providers p ON p.provider_id = s.provider_id
            JOIN services sv ON sv.service_id = s.service_id
            """;

    // Bookable means OPEN and not yet started.
    private static final String FIND_AVAILABLE_SELECT_SQL =
            SLOT_DTO_SELECT_SQL + " WHERE s.status = 'OPEN' AND s.start_time > CURRENT_TIMESTAMP";

    private static final String PROVIDER_ID_FILTER_SQL = " AND s.provider_id = ?";

    private static final String PROVIDER_TYPE_FILTER_SQL = " AND p.type = ?";

    private static final String SERVICE_ID_FILTER_SQL = " AND s.service_id = ?";

    private static final String START_AT_OR_AFTER_SQL = " AND s.start_time >= ?";

    private static final String START_BEFORE_SQL = " AND s.start_time < ?";

    private static final String PAGE_SQL = " ORDER BY s.start_time, s.slot_id LIMIT ? OFFSET ?";

    private static final String COUNT_OPEN_SQL =
            "SELECT COUNT(*) FROM availability_slots WHERE status = 'OPEN' AND start_time > CURRENT_TIMESTAMP";

    private static final String FIND_DTO_BY_ID_SQL = SLOT_DTO_SELECT_SQL + " WHERE s.slot_id = ?";

    private static final String FIND_FOR_UPDATE_SQL = """
            SELECT slot_id, provider_id, service_id, start_time, end_time, status
            FROM availability_slots
            WHERE slot_id = ?
            FOR UPDATE
            """;

    private static final String UPDATE_STATUS_SQL =
            "UPDATE availability_slots SET status = ? WHERE slot_id = ? AND status = ?";

    private static final String DELETE_SQL = "DELETE FROM availability_slots WHERE slot_id = ?";

    private static final RowMapper<SlotDto> SLOT_DTO_MAPPER = (rs, rowNum) -> new SlotDto(
            rs.getLong("slot_id"),
            rs.getString("provider_name"),
            rs.getString("service_name"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getBigDecimal("price"));

    private static final RowMapper<SlotRecord> SLOT_RECORD_MAPPER = (rs, rowNum) -> new SlotRecord(
            rs.getLong("slot_id"),
            rs.getLong("provider_id"),
            rs.getLong("service_id"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("status"));

    private final JdbcTemplate jdbcTemplate;

    public SlotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SlotDto> findAvailable(Long providerId, String providerType, Long serviceId,
                                       LocalDate date, int limit, long offset) {
        StringBuilder sql = new StringBuilder(FIND_AVAILABLE_SELECT_SQL);
        List<Object> params = new ArrayList<>();
        if (providerId != null) {
            sql.append(PROVIDER_ID_FILTER_SQL);
            params.add(providerId);
        }
        if (providerType != null) {
            sql.append(PROVIDER_TYPE_FILTER_SQL);
            params.add(providerType);
        }
        if (serviceId != null) {
            sql.append(SERVICE_ID_FILTER_SQL);
            params.add(serviceId);
        }
        if (date != null) {
            sql.append(START_AT_OR_AFTER_SQL);
            params.add(Timestamp.valueOf(date.atStartOfDay()));
            sql.append(START_BEFORE_SQL);
            params.add(Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        }
        sql.append(PAGE_SQL);
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), SLOT_DTO_MAPPER, params.toArray());
    }

    public int countOpen() {
        Integer count = jdbcTemplate.queryForObject(COUNT_OPEN_SQL, Integer.class);
        return count == null ? 0 : count;
    }

    /** Loads a slot and row-locks it until the surrounding transaction ends (pessimistic lock). */
    public Optional<SlotRecord> findByIdForUpdate(long slotId) {
        return jdbcTemplate.query(FIND_FOR_UPDATE_SQL, SLOT_RECORD_MAPPER, slotId).stream().findFirst();
    }

    public Optional<SlotDto> findDtoById(long slotId) {
        return jdbcTemplate.query(FIND_DTO_BY_ID_SQL, SLOT_DTO_MAPPER, slotId).stream().findFirst();
    }

    /** Compare-and-set status change; returns 0 when the slot was not in {@code fromStatus}. */
    public int updateStatus(long slotId, String fromStatus, String toStatus) {
        return jdbcTemplate.update(UPDATE_STATUS_SQL, toStatus, slotId, fromStatus);
    }

    public int deleteSlot(long slotId) {
        return jdbcTemplate.update(DELETE_SQL, slotId);
    }

    public record SlotRecord(long slotId, long providerId, long serviceId, LocalDateTime startTime,
                             LocalDateTime endTime, String status) { }
}
