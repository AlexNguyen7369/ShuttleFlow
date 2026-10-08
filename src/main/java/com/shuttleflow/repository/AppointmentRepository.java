package com.shuttleflow.repository;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.ProviderAppointmentDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AppointmentRepository {

    // active_slot_id = slot_id marks the row as the slot's one active booking (see schema.sql).
    private static final String INSERT_SQL =
            "INSERT INTO appointments (slot_id, user_id, status, active_slot_id) VALUES (?, ?, 'BOOKED', ?)";

    private static final String CUSTOMER_SELECT_SQL = """
            SELECT a.appointment_id, a.slot_id, p.name AS provider_name, sv.name AS service_name,
                   s.start_time, s.end_time,
                   CASE WHEN a.status = 'CANCELLED' THEN 'CANCELLED'
                        WHEN s.start_time <= CURRENT_TIMESTAMP THEN 'COMPLETED'
                        ELSE 'BOOKED' END AS display_status
            FROM appointments a
            JOIN availability_slots s ON s.slot_id = a.slot_id
            JOIN providers p ON p.provider_id = s.provider_id
            JOIN services sv ON sv.service_id = s.service_id
            """;

    private static final String FIND_ONE_FOR_CUSTOMER_SQL =
            CUSTOMER_SELECT_SQL + " WHERE a.appointment_id = ? AND a.user_id = ?";

    private static final String FIND_UPCOMING_SQL = CUSTOMER_SELECT_SQL
            + " WHERE a.user_id = ? AND a.status = 'BOOKED' AND s.start_time > CURRENT_TIMESTAMP"
            + " ORDER BY s.start_time ASC, a.appointment_id ASC";

    private static final String FIND_HISTORY_SQL = CUSTOMER_SELECT_SQL
            + " WHERE a.user_id = ? AND (a.status = 'CANCELLED' OR s.start_time <= CURRENT_TIMESTAMP)"
            + " ORDER BY s.start_time DESC, a.appointment_id DESC";

    private static final String FIND_FOR_UPDATE_SQL = """
            SELECT a.appointment_id, a.slot_id, a.user_id, a.status, s.start_time, s.provider_id
            FROM appointments a
            JOIN availability_slots s ON s.slot_id = a.slot_id
            WHERE a.appointment_id = ?
            FOR UPDATE
            """;

    private static final String CANCEL_SQL =
            "UPDATE appointments SET status = 'CANCELLED', active_slot_id = NULL"
            + " WHERE appointment_id = ? AND status = 'BOOKED'";

    private static final String HAS_ACTIVE_SQL =
            "SELECT 1 FROM appointments WHERE slot_id = ? AND status = 'BOOKED'";

    private static final String HAS_ANY_SQL = "SELECT 1 FROM appointments WHERE slot_id = ?";

    private static final String FIND_PROVIDER_APPOINTMENTS_SQL = """
            SELECT a.appointment_id, a.slot_id, sv.name AS service_name,
                   s.start_time, s.end_time, u.full_name AS customer_name, u.email AS customer_email
            FROM appointments a
            JOIN users u ON u.user_id = a.user_id
            JOIN availability_slots s ON s.slot_id = a.slot_id
            JOIN services sv ON sv.service_id = s.service_id
            WHERE s.provider_id = ? AND a.status = 'BOOKED'
            ORDER BY s.start_time ASC, a.appointment_id ASC
            """;

    private static final RowMapper<AppointmentDto> APPOINTMENT_MAPPER = (rs, rowNum) -> new AppointmentDto(
            rs.getLong("appointment_id"),
            rs.getLong("slot_id"),
            rs.getString("provider_name"),
            rs.getString("service_name"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getTimestamp("end_time").toLocalDateTime(),
            rs.getString("display_status"));

    private static final RowMapper<ProviderAppointmentDto> PROVIDER_APPOINTMENT_MAPPER =
            (rs, rowNum) -> new ProviderAppointmentDto(
                    rs.getLong("appointment_id"),
                    rs.getLong("slot_id"),
                    rs.getString("service_name"),
                    rs.getTimestamp("start_time").toLocalDateTime(),
                    rs.getTimestamp("end_time").toLocalDateTime(),
                    rs.getString("customer_name"),
                    rs.getString("customer_email"));

    private static final RowMapper<AppointmentRecord> RECORD_MAPPER = (rs, rowNum) -> new AppointmentRecord(
            rs.getLong("appointment_id"),
            rs.getLong("slot_id"),
            rs.getLong("user_id"),
            rs.getString("status"),
            rs.getTimestamp("start_time").toLocalDateTime(),
            rs.getLong("provider_id"));

    private static final ResultSetExtractor<Boolean> EXISTS = rs -> rs.next();

    private final JdbcTemplate jdbcTemplate;

    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Inserts a BOOKED appointment; a second active booking violates appointments_one_active_booking. */
    public long insert(long slotId, long userId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            // Naming the key column keeps PostgreSQL from returning every column as a "generated key".
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL, new String[] {"appointment_id"});
            statement.setLong(1, slotId);
            statement.setLong(2, userId);
            statement.setLong(3, slotId);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Appointment ID was not generated.");
        }
        return key.longValue();
    }

    public Optional<AppointmentDto> findCustomerAppointment(long appointmentId, long userId) {
        return jdbcTemplate.query(FIND_ONE_FOR_CUSTOMER_SQL, APPOINTMENT_MAPPER, appointmentId, userId)
                .stream().findFirst();
    }

    public List<AppointmentDto> findCustomerAppointments(long userId, boolean history) {
        return jdbcTemplate.query(history ? FIND_HISTORY_SQL : FIND_UPCOMING_SQL, APPOINTMENT_MAPPER, userId);
    }

    /** Loads an appointment and row-locks it until the surrounding transaction ends. */
    public Optional<AppointmentRecord> findByIdForUpdate(long appointmentId) {
        return jdbcTemplate.query(FIND_FOR_UPDATE_SQL, RECORD_MAPPER, appointmentId).stream().findFirst();
    }

    public int cancel(long appointmentId) {
        return jdbcTemplate.update(CANCEL_SQL, appointmentId);
    }

    public boolean hasActiveAppointment(long slotId) {
        return Boolean.TRUE.equals(jdbcTemplate.query(HAS_ACTIVE_SQL, EXISTS, slotId));
    }

    /** True when any appointment row (active or cancelled history) references the slot. */
    public boolean hasAnyAppointment(long slotId) {
        return Boolean.TRUE.equals(jdbcTemplate.query(HAS_ANY_SQL, EXISTS, slotId));
    }

    public List<ProviderAppointmentDto> findProviderAppointments(long providerId) {
        return jdbcTemplate.query(FIND_PROVIDER_APPOINTMENTS_SQL, PROVIDER_APPOINTMENT_MAPPER, providerId);
    }

    public record AppointmentRecord(long appointmentId, long slotId, long userId, String status,
                                    LocalDateTime startTime, long providerId) { }
}
