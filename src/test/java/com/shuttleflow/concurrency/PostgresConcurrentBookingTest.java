package com.shuttleflow.concurrency;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.BookingConflictException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same-slot race against a real PostgreSQL server, where {@code SELECT ... FOR UPDATE} row locks and
 * READ COMMITTED behave exactly as in production. Opt-in because it needs a running server; the schema
 * drops and recreates tables, so point it at a throwaway database:
 *
 * <pre>SHUTTLEFLOW_PG_TEST_URL=jdbc:postgresql://localhost:5432/shuttleflow_test mvn test</pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SHUTTLEFLOW_PG_TEST_URL", matches = "jdbc:postgresql:.+")
class PostgresConcurrentBookingTest {

    private static final int THREADS = 8;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppointmentService appointmentService;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("SHUTTLEFLOW_PG_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> envOr("SHUTTLEFLOW_PG_TEST_USER", "shuttleflow"));
        registry.add("spring.datasource.password", () -> envOr("SHUTTLEFLOW_PG_TEST_PASSWORD", ""));
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> THREADS + 2);
    }

    @RepeatedTest(10)
    void manyCustomersRacingForOneSlotYieldExactlyOneBooking() throws Exception {
        assertEquals("PostgreSQL", jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName()));
        long slotId = freshSlot();

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                long userId = i % 2 == 0 ? 3L : 4L; // the two seeded customers
                futures.add(pool.submit(() -> attempt(slotId, userId, ready, go)));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<Object> future : futures) {
                Object result = future.get(10, TimeUnit.SECONDS);
                if (result instanceof AppointmentDto) {
                    successes++;
                } else if (result instanceof BookingConflictException) {
                    conflicts++;
                }
            }
            assertEquals(1, successes);
            assertEquals(THREADS - 1, conflicts);
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'", Integer.class, slotId));
            assertEquals("BOOKED", jdbc.queryForObject(
                    "SELECT status FROM availability_slots WHERE slot_id = ?", String.class, slotId));
        } finally {
            pool.shutdownNow();
        }
    }

    private long freshSlot() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
        LocalDateTime start = LocalDateTime.now().plusDays(3).withNano(0);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)"
                + " VALUES (1, 1, ?, ?, 'OPEN')", start, start.plusHours(1));
        return jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 1 AND start_time = ?",
                Long.class, start);
    }

    private Object attempt(long slotId, long userId, CountDownLatch ready, CountDownLatch go) {
        HttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.ATTRIBUTE,
                new UserSession(userId, "customer" + userId + "@example.com", "Customer", "CUSTOMER", null));
        ready.countDown();
        try {
            go.await();
            return appointmentService.book(new BookingRequest(slotId, null), session);
        } catch (BookingConflictException e) {
            return e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
