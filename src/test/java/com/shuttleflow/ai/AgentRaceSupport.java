package com.shuttleflow.ai;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.service.AiConversationService;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.SlotService;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Shared by AgentDoubleBookingTest (H2) and the opt-in PostgreSQL race test. */
public final class AgentRaceSupport {
    private AgentRaceSupport() {
    }

    public static long freshSlot(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM ai_conversations");
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
        LocalDateTime start = LocalDateTime.now().plusDays(3).withNano(0);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)"
                + " VALUES (1, 1, ?, ?, 'OPEN')", start, start.plusHours(1));
        return jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 1 AND start_time = ?",
                Long.class, start);
    }

    public static HttpSession customer(long userId) {
        HttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.ATTRIBUTE,
                new UserSession(userId, "customer" + userId + "@example.com", "Customer", "CUSTOMER", null));
        return session;
    }

    public static int activeBookings(JdbcTemplate jdbc, long slotId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE active_slot_id = ?", Integer.class, slotId);
    }

    /** Returns the ids of {@code count} customer accounts, creating test-only ones as needed. */
    public static List<Long> customers(JdbcTemplate jdbc, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String email = "race" + i + "@example.com";
            jdbc.update("INSERT INTO users (email, password_hash, full_name, role) SELECT ?, 'not-a-login', ?, 'CUSTOMER'"
                    + " WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = ?)", email, "Racer " + i, email);
            ids.add(jdbc.queryForObject("SELECT user_id FROM users WHERE email = ?", Long.class, email));
        }
        return ids;
    }

    /**
     * Each customer proposes in turn 1, then all confirm in turn 2 at the same moment. One customer per
     * thread, because the assistant keeps one conversation (and one pending action) per user.
     */
    public static List<String> race(List<Long> customerIds, long slotId, SlotService slotService,
                                    AppointmentService appointmentService, AiConversationService conversations)
            throws Exception {
        int threads = customerIds.size();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                long userId = customerIds.get(i);
                HttpSession session = customer(userId);
                futures.add(pool.submit(() -> {
                    new BookingTools(slotService, appointmentService, conversations, session, userId, 1)
                            .proposeBooking(slotId);
                    ready.countDown();
                    go.await();
                    return new BookingTools(slotService, appointmentService, conversations, session, userId, 2)
                            .confirmBooking();
                }));
            }
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("threads did not get ready");
            }
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
