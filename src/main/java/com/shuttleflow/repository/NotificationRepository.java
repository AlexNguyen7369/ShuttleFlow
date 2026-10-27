package com.shuttleflow.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** The notifications outbox. Every status change is guarded by status = 'PENDING' so racing senders cannot regress a row. */
@Repository
public class NotificationRepository {

    private static final String INSERT_SQL = """
            INSERT INTO notifications (appointment_id, idempotency_key, type, recipient, message)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String FIND_PENDING_SQL = """
            SELECT notification_id, appointment_id, idempotency_key, type, recipient, message, attempts
            FROM notifications
            WHERE notification_id = ? AND status = 'PENDING'
            """;

    private static final String FIND_DUE_SQL = """
            SELECT notification_id FROM notifications
            WHERE status = 'PENDING' AND created_at < ?
            ORDER BY notification_id ASC
            LIMIT ?
            """;

    private static final String FINISH_SQL = """
            UPDATE notifications SET status = ?, attempts = attempts + 1, last_attempt_at = ?
            WHERE notification_id = ? AND status = 'PENDING'
            """;

    // A failed round stays PENDING for the poller until maxRounds, then becomes FAILED (dead letter).
    private static final String RECORD_FAILED_ROUND_SQL = """
            UPDATE notifications
            SET attempts = attempts + 1, last_attempt_at = ?,
                status = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END
            WHERE notification_id = ? AND status = 'PENDING'
            """;

    private static final String COUNT_PENDING_SQL = "SELECT COUNT(*) FROM notifications WHERE status = 'PENDING'";

    private static final RowMapper<NotificationRecord> RECORD_MAPPER = (rs, rowNum) -> new NotificationRecord(
            rs.getLong("notification_id"),
            rs.getLong("appointment_id"),
            rs.getString("idempotency_key"),
            rs.getString("type"),
            rs.getString("recipient"),
            rs.getString("message"),
            rs.getInt("attempts"));

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(long appointmentId, String idempotencyKey, String type, String recipient, String message) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT_SQL, new String[] {"notification_id"});
            statement.setLong(1, appointmentId);
            statement.setString(2, idempotencyKey);
            statement.setString(3, type);
            statement.setString(4, recipient);
            statement.setString(5, message);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Notification ID was not generated.");
        }
        return key.longValue();
    }

    public Optional<NotificationRecord> findPending(long notificationId) {
        return jdbcTemplate.query(FIND_PENDING_SQL, RECORD_MAPPER, notificationId).stream().findFirst();
    }

    /** PENDING rows created before the cutoff, oldest first. */
    public List<Long> findDue(LocalDateTime createdBefore, int limit) {
        return jdbcTemplate.queryForList(FIND_DUE_SQL, Long.class, Timestamp.valueOf(createdBefore), limit);
    }

    public int markSent(long notificationId, LocalDateTime now) {
        return jdbcTemplate.update(FINISH_SQL, "SENT", Timestamp.valueOf(now), notificationId);
    }

    public int markFailed(long notificationId, LocalDateTime now) {
        return jdbcTemplate.update(FINISH_SQL, "FAILED", Timestamp.valueOf(now), notificationId);
    }

    public int recordFailedRound(long notificationId, LocalDateTime now, int maxRounds) {
        return jdbcTemplate.update(RECORD_FAILED_ROUND_SQL, Timestamp.valueOf(now), maxRounds, notificationId);
    }

    public long countPending() {
        Long count = jdbcTemplate.queryForObject(COUNT_PENDING_SQL, Long.class);
        return count == null ? 0 : count;
    }

    public record NotificationRecord(long notificationId, long appointmentId, String idempotencyKey, String type,
                                     String recipient, String message, int attempts) { }
}
