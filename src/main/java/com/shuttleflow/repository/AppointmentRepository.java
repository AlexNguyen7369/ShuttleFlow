package com.shuttleflow.repository;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.ProviderAppointmentDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AppointmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public AppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(long slotId, long userId) {
        jdbcTemplate.update("INSERT INTO appointments (slot_id, user_id, status, active_slot_id) VALUES (?, ?, 'BOOKED', ?)",
                slotId, userId, slotId);
        Long appointmentId = jdbcTemplate.queryForObject("""
                SELECT appointment_id FROM appointments
                WHERE slot_id = ? AND user_id = ? AND status = 'BOOKED'
                ORDER BY appointment_id DESC LIMIT 1
                """, Long.class, slotId, userId);
        if (appointmentId == null) {
            throw new IllegalStateException("Appointment ID was not generated.");
        }
        return appointmentId;
    }

    public Optional<AppointmentDto> findCustomerAppointment(long appointmentId, long userId) {
        List<AppointmentDto> rows = jdbcTemplate.query("""
                SELECT a.appointment_id, a.slot_id, p.name AS provider_name, sv.name AS service_name,
                       s.start_time, s.end_time,
                       CASE WHEN a.status = 'CANCELLED' THEN 'CANCELLED'
                            WHEN s.start_time < CURRENT_TIMESTAMP THEN 'COMPLETED'
                            ELSE 'BOOKED' END AS display_status
                FROM appointments a
                JOIN availability_slots s ON s.slot_id = a.slot_id
                JOIN providers p ON p.provider_id = s.provider_id
                JOIN services sv ON sv.service_id = s.service_id
                WHERE a.appointment_id = ? AND a.user_id = ?
                """, appointmentMapper(), appointmentId, userId);
        return rows.stream().findFirst();
    }

    public List<AppointmentDto> findCustomerAppointments(long userId, boolean history) {
        String sql = """
                SELECT a.appointment_id, a.slot_id, p.name AS provider_name, sv.name AS service_name,
                       s.start_time, s.end_time,
                       CASE WHEN a.status = 'CANCELLED' THEN 'CANCELLED'
                            WHEN s.start_time < CURRENT_TIMESTAMP THEN 'COMPLETED'
                            ELSE 'BOOKED' END AS display_status
                FROM appointments a
                JOIN availability_slots s ON s.slot_id = a.slot_id
                JOIN providers p ON p.provider_id = s.provider_id
                JOIN services sv ON sv.service_id = s.service_id
                WHERE a.user_id = ?
                """ + (history
                ? " AND (a.status = 'CANCELLED' OR s.start_time <= CURRENT_TIMESTAMP) ORDER BY s.start_time DESC, a.appointment_id DESC"
                : " AND a.status = 'BOOKED' AND s.start_time > CURRENT_TIMESTAMP ORDER BY s.start_time ASC, a.appointment_id ASC");
        return jdbcTemplate.query(sql, appointmentMapper(), userId);
    }

    public Optional<AppointmentRecord> findByIdForUpdate(long appointmentId) {
        List<AppointmentRecord> rows = jdbcTemplate.query("""
                SELECT a.appointment_id, a.slot_id, a.user_id, a.status,
                       s.start_time, s.provider_id
                FROM appointments a
                JOIN availability_slots s ON s.slot_id = a.slot_id
                WHERE a.appointment_id = ? FOR UPDATE
                """, (rs, rowNum) -> new AppointmentRecord(rs.getLong("appointment_id"),
                rs.getLong("slot_id"), rs.getLong("user_id"), rs.getString("status"),
                rs.getTimestamp("start_time").toLocalDateTime(), rs.getLong("provider_id")), appointmentId);
        return rows.stream().findFirst();
    }

    public int cancel(long appointmentId) {
        return jdbcTemplate.update("UPDATE appointments SET status = 'CANCELLED', active_slot_id = NULL WHERE appointment_id = ? AND status = 'BOOKED'",
                appointmentId);
    }

    public boolean hasActiveAppointment(long slotId) {
        Boolean found = jdbcTemplate.query("SELECT 1 FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(), slotId);
        return Boolean.TRUE.equals(found);
    }

    public List<ProviderAppointmentDto> findProviderAppointments(long providerId) {
        return jdbcTemplate.query("""
                SELECT a.appointment_id, a.slot_id, sv.name AS service_name,
                       s.start_time, s.end_time, u.full_name AS customer_name, u.email AS customer_email
                FROM appointments a
                JOIN users u ON u.user_id = a.user_id
                JOIN availability_slots s ON s.slot_id = a.slot_id
                JOIN services sv ON sv.service_id = s.service_id
                WHERE s.provider_id = ? AND a.status = 'BOOKED'
                ORDER BY s.start_time ASC, a.appointment_id ASC
                """, (rs, rowNum) -> new ProviderAppointmentDto(rs.getLong("appointment_id"),
                rs.getLong("slot_id"), rs.getString("service_name"),
                rs.getTimestamp("start_time").toLocalDateTime(), rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getString("customer_name"), rs.getString("customer_email")), providerId);
    }

    private org.springframework.jdbc.core.RowMapper<AppointmentDto> appointmentMapper() {
        return (rs, rowNum) -> new AppointmentDto(rs.getLong("appointment_id"), rs.getLong("slot_id"),
                rs.getString("provider_name"), rs.getString("service_name"),
                rs.getTimestamp("start_time").toLocalDateTime(), rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getString("display_status"));
    }

    public record AppointmentRecord(long appointmentId, long slotId, long userId, String status,
                                    LocalDateTime startTime, long providerId) { }
}
