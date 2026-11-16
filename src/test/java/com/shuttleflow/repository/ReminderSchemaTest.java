package com.shuttleflow.repository;

import com.shuttleflow.dto.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ReminderSchemaTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private NotificationRepository notificationRepository;

    @Test
    void outboxAcceptsTheReminderType() {
        notificationRepository.insert(1L, "REMINDER:1", NotificationRequest.APPOINTMENT_REMINDER,
                "alex@shuttleflow.com", "Reminder: Court 3 today.");
        assertThat(jdbc.queryForObject("SELECT type FROM notifications WHERE idempotency_key = 'REMINDER:1'",
                String.class)).isEqualTo("APPOINTMENT_REMINDER");
    }

    @Test
    void seedHasABookedAppointmentStartingWithin24Hours() {
        Integer due = jdbc.queryForObject("""
                SELECT COUNT(*) FROM appointments a JOIN availability_slots s ON s.slot_id = a.slot_id
                WHERE a.status = 'BOOKED' AND s.start_time > LOCALTIMESTAMP
                  AND s.start_time <= LOCALTIMESTAMP + INTERVAL '24' HOUR""", Integer.class);
        assertThat(due).isGreaterThanOrEqualTo(1);
    }
}
