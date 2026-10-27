package com.shuttleflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuttleflow.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Not @Transactional: bookings must really commit so the after-commit listener fires. The test
 * base-url points at a refusing port, so the notification service is down for every test here.
 * The deletes really commit, so the context is rebuilt afterwards (schema.sql + seed.sql reload).
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private NotificationService notificationService;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
    }

    @Test
    void bookingSucceedsWhileNotificationServiceIsDownAndOutboxKeepsThePendingNotification() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(3));
        long appointmentId = book(login("alex@shuttleflow.com", "alex-test"), slotId);

        assertEquals(1, count("SELECT COUNT(*) FROM appointments WHERE slot_id = " + slotId + " AND status = 'BOOKED'"));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM notifications WHERE idempotency_key = ?", String.class, "BOOKED:" + appointmentId));
        awaitAttempts("BOOKED:" + appointmentId);
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM notifications WHERE idempotency_key = ?", String.class, "BOOKED:" + appointmentId));
    }

    @Test
    void cancelSucceedsWhileNotificationServiceIsDownAndQueuesCancelledNotification() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(3));
        MockHttpSession customer = login("alex@shuttleflow.com", "alex-test");
        long appointmentId = book(customer, slotId);

        mvc.perform(delete("/appointments/" + appointmentId).session(customer)).andExpect(status().isNoContent());
        assertEquals(1, count("SELECT COUNT(*) FROM notifications WHERE idempotency_key = 'CANCELLED:" + appointmentId + "'"));
    }

    @Test
    void rolledBackBookingWritesNoNotification() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(3));
        book(login("alex@shuttleflow.com", "alex-test"), slotId);

        mvc.perform(post("/appointments").session(login("jamie@shuttleflow.com", "jamie-test"))
                        .contentType("application/json").content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isConflict());
        assertEquals(1, count("SELECT COUNT(*) FROM notifications"));
    }

    @Test
    void pollerRetriesStaleRowsAndDeadLettersAfterMaxRounds() throws Exception {
        long slotId = insertSlot(LocalDateTime.now().plusDays(3));
        String key = "BOOKED:" + book(login("alex@shuttleflow.com", "alex-test"), slotId);
        awaitAttempts(key);

        // Fresh rows are left to the fast path.
        notificationService.sendDue();
        assertEquals(1, attempts(key));

        jdbc.update("UPDATE notifications SET created_at = ? WHERE idempotency_key = ?",
                LocalDateTime.now().minusMinutes(2), key);
        notificationService.sendDue();
        assertEquals(2, attempts(key));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM notifications WHERE idempotency_key = ?", String.class, key));

        jdbc.update("UPDATE notifications SET attempts = 4 WHERE idempotency_key = ?", key);
        notificationService.sendDue();
        assertEquals("FAILED", jdbc.queryForObject(
                "SELECT status FROM notifications WHERE idempotency_key = ?", String.class, key));
        notificationService.sendDue();
        assertEquals(5, attempts(key));
    }

    private int attempts(String key) {
        return jdbc.queryForObject("SELECT attempts FROM notifications WHERE idempotency_key = ?", Integer.class, key);
    }

    /** The after-commit listener runs on another thread; wait for its failed round to be recorded. */
    private void awaitAttempts(String key) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Integer attempts = jdbc.queryForObject("SELECT attempts FROM notifications WHERE idempotency_key = ?",
                    Integer.class, key);
            if (attempts != null && attempts > 0) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("after-commit send never ran for " + key);
    }

    private long book(MockHttpSession session, long slotId) throws Exception {
        String body = mvc.perform(post("/appointments").session(session).contentType("application/json")
                        .content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("appointmentId").asLong();
    }

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    private long insertSlot(LocalDateTime start) {
        jdbc.update("""
                INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)
                VALUES (1, 1, ?, ?, 'OPEN')
                """, start, start.plusHours(1));
        return jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 1 AND start_time = ?",
                Long.class, start);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }
}
