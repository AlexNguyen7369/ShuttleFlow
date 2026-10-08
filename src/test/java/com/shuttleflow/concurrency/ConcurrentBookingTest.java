package com.shuttleflow.concurrency;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.BookingConflictException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConcurrentBookingTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AppointmentService appointmentService;
    private long slotId;

    @BeforeEach
    void prepareSlot() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
        LocalDateTime start = LocalDateTime.now().plusDays(3).withNano(0);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status) VALUES (1, 1, ?, ?, 'OPEN')",
                start, start.plusHours(1));
        slotId = jdbc.queryForObject("SELECT slot_id FROM availability_slots WHERE provider_id = 1 AND start_time = ?",
                Long.class, start);
    }

    @Test
    void twoCustomersBookingSameSlotProduceOneSuccessAndOneConflict() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> attempt(3L, "alex@shuttleflow.com", ready, start)));
            futures.add(executor.submit(() -> attempt(4L, "jamie@shuttleflow.com", ready, start)));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Object> results = List.of(futures.get(0).get(), futures.get(1).get());
            assertEquals(1, results.stream().filter(AppointmentDto.class::isInstance).count());
            assertEquals(1, results.stream().filter(BookingConflictException.class::isInstance).count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE active_slot_id = ?", Integer.class, slotId));
            assertEquals("BOOKED", jdbc.queryForObject("SELECT status FROM availability_slots WHERE slot_id = ?", String.class, slotId));
        } finally {
            executor.shutdownNow();
        }
    }

    private Object attempt(long userId, String email, CountDownLatch ready, CountDownLatch start) {
        HttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.ATTRIBUTE, new UserSession(userId, email, email, "CUSTOMER", null));
        ready.countDown();
        try {
            start.await();
            return appointmentService.book(new BookingRequest(slotId, null), session);
        } catch (BookingConflictException e) {
            return e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
