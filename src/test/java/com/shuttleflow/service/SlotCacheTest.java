package com.shuttleflow.service;

import com.shuttleflow.auth.UserSession;
import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.AvailabilityRequest;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Honors: GET /slots is cached, and every booking, cancellation and provider slot change evicts it,
 * so browsing never shows a stale slot. Uses the in-memory "simple" cache so no Redis server is needed.
 */
@SpringBootTest(properties = "spring.cache.type=simple")
@Transactional
class SlotCacheTest {

    private static final UserSession ALEX = new UserSession(3, "alex@shuttleflow.com", "Alex Nguyen", "CUSTOMER", null);
    private static final UserSession COURT = new UserSession(1, "court.manager@shuttleflow.com", "Court Manager", "PROVIDER", 1L);

    @Autowired private SlotService slotService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private ProviderService providerService;
    @Autowired private CacheManager cacheManager;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private SlotRepository slotRepository;

    private long slotId;

    @BeforeEach
    void oneOpenSlot() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
        LocalDateTime start = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status) VALUES (1, 1, ?, ?, 'OPEN')",
                start, start.plusHours(1));
        slotId = jdbc.queryForObject("SELECT slot_id FROM availability_slots", Long.class);
        cacheManager.getCache(SlotService.OPEN_SLOTS_CACHE).clear();
        clearInvocations(slotRepository);
    }

    @Test
    void secondIdenticalBrowseIsServedFromTheCache() {
        List<SlotDto> first = browse();
        List<SlotDto> second = browse();

        assertThat(second).extracting(SlotDto::getSlotId).containsExactly(slotId);
        assertThat(first).extracting(SlotDto::getSlotId).isEqualTo(second.stream().map(SlotDto::getSlotId).toList());
        verify(slotRepository, times(1)).findAvailable(any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    void differentFiltersAreCachedSeparately() {
        browse();
        slotService.getAvailableSlots(null, "COACHING", null, null, 1);

        verify(slotRepository, times(2)).findAvailable(any(), any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    void bookingEvictsSoTheSlotDisappears() {
        assertThat(browse()).hasSize(1);

        appointmentService.book(new BookingRequest(slotId, null), ALEX);

        assertThat(browse()).isEmpty();
    }

    @Test
    void cancellingEvictsSoTheSlotReappears() {
        AppointmentDto booked = appointmentService.book(new BookingRequest(slotId, null), ALEX);
        assertThat(browse()).isEmpty();

        appointmentService.cancel(booked.getAppointmentId(), ALEX);

        assertThat(browse()).extracting(SlotDto::getSlotId).containsExactly(slotId);
    }

    @Test
    void providerSlotChangesEvict() {
        assertThat(browse()).hasSize(1);
        LocalDateTime start = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.HOURS);

        SlotDto created = providerService.create(new AvailabilityRequest(1L, start, start.plusHours(1)), COURT);
        assertThat(browse()).hasSize(2);

        providerService.remove(created.getSlotId(), COURT);
        assertThat(browse()).extracting(SlotDto::getSlotId).containsExactly(slotId);
    }

    private List<SlotDto> browse() {
        return slotService.getAvailableSlots(null, null, null, null, 1);
    }
}
