package com.shuttleflow.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 02: GET /slots?providerId=&sessionType=&page=
 * Each test starts from an empty availability_slots table (rolled back afterwards)
 * and inserts only the slots it needs, using the providers/services from seed.sql.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SlotControllerBrowseTest {

    // Browsing only lists future slots, so the fixtures are anchored 30 days ahead of today.
    private static final LocalDateTime BASE = LocalDate.now().plusDays(30).atTime(8, 0);

    /** The JSON timestamp of BASE + offsetHours, as Jackson serializes LocalDateTime. */
    private static String at(int offsetHours) {
        return BASE.plusHours(offsetHours).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private long courtId;
    private long coachId;

    @BeforeEach
    void clearSlots() {
        jdbc.update("DELETE FROM appointments");
        jdbc.update("DELETE FROM availability_slots");
        courtId = jdbc.queryForObject("SELECT provider_id FROM providers WHERE name = ?", Long.class, "Court 3");
        coachId = jdbc.queryForObject("SELECT provider_id FROM providers WHERE name = ?", Long.class, "Coach Kim");
    }

    /** Inserts a 1-hour slot BASE + offsetHours for the provider using its first service. */
    private void slot(long providerId, int offsetHours, String status) {
        long serviceId = jdbc.queryForObject(
                "SELECT MIN(service_id) FROM services WHERE provider_id = ?", Long.class, providerId);
        LocalDateTime start = BASE.plusHours(offsetHours);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)"
                        + " VALUES (?, ?, ?, ?, ?)",
                providerId, serviceId, start, start.plusHours(1), status);
    }

    private void openSlots(long providerId, int count, int firstOffset) {
        for (int i = 0; i < count; i++) {
            slot(providerId, firstOffset + i, "OPEN");
        }
    }

    // --- pagination ---

    @Test
    void noFilters_returnsFirstPageOfOpenSlotsOrderedByStartTime() throws Exception {
        // insert out of order: offsets 14..0 (15 slots); first page must be offsets 0..9
        for (int i = 14; i >= 0; i--) {
            slot(courtId, i, "OPEN");
        }
        mvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].startTime").value(at(0)))
                .andExpect(jsonPath("$[1].startTime").value(at(1)))
                .andExpect(jsonPath("$[9].startTime").value(at(9)));
    }

    @Test
    void eachPageHasAtMostTenSlots_offsetIsPageMinusOneTimesTen() throws Exception {
        openSlots(courtId, 25, 0);
        mvc.perform(get("/slots").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].startTime").value(at(10)))
                .andExpect(jsonPath("$[9].startTime").value(at(19)));
        mvc.perform(get("/slots").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].startTime").value(at(20)));
    }

    @Test
    void pageDefaultsToOne() throws Exception {
        openSlots(courtId, 12, 0);
        mvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].startTime").value(at(0)));
    }

    @Test
    void pagePastLast_returnsOkWithEmptyList() throws Exception {
        openSlots(courtId, 12, 0);
        mvc.perform(get("/slots").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void hugePageNumber_returnsOkWithEmptyList() throws Exception {
        openSlots(courtId, 12, 0);
        mvc.perform(get("/slots").param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // --- filters ---

    @Test
    void providerIdFilter_returnsOnlyThatProvidersSlots() throws Exception {
        openSlots(courtId, 3, 0);
        openSlots(coachId, 2, 10);
        mvc.perform(get("/slots").param("providerId", String.valueOf(coachId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.providerName != 'Coach Kim')]").isEmpty());
    }

    @Test
    void sessionTypeOpenPlay_returnsOnlyCourtProviderSlots() throws Exception {
        openSlots(courtId, 3, 0);
        openSlots(coachId, 2, 10);
        mvc.perform(get("/slots").param("sessionType", "OPEN_PLAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.providerName != 'Court 3')]").isEmpty());
    }

    @Test
    void sessionTypeCoaching_returnsOnlyCoachProviderSlots() throws Exception {
        openSlots(courtId, 3, 0);
        openSlots(coachId, 2, 10);
        mvc.perform(get("/slots").param("sessionType", "COACHING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.providerName != 'Coach Kim')]").isEmpty());
    }

    @Test
    void filtersCombine_providerIdAndSessionTypeThatDisagreeReturnEmpty() throws Exception {
        openSlots(courtId, 3, 0);
        openSlots(coachId, 2, 10);
        mvc.perform(get("/slots")
                        .param("providerId", String.valueOf(coachId))
                        .param("sessionType", "OPEN_PLAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void filtersCombine_providerIdAndMatchingSessionTypeReturnProviderSlots() throws Exception {
        openSlots(courtId, 3, 0);
        openSlots(coachId, 2, 10);
        mvc.perform(get("/slots")
                        .param("providerId", String.valueOf(coachId))
                        .param("sessionType", "COACHING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void filtersCombineWithPagination() throws Exception {
        openSlots(courtId, 13, 0);
        openSlots(coachId, 4, 20);
        mvc.perform(get("/slots")
                        .param("providerId", String.valueOf(courtId))
                        .param("sessionType", "OPEN_PLAY")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value(at(10)))
                .andExpect(jsonPath("$[?(@.providerName != 'Court 3')]").isEmpty());
    }

    // --- status ---

    @Test
    void bookedAndCancelledSlotsNeverAppear() throws Exception {
        slot(courtId, 0, "OPEN");
        slot(courtId, 1, "BOOKED");
        slot(courtId, 2, "CANCELLED");
        slot(courtId, 3, "OPEN");
        mvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startTime").value(at(0)))
                .andExpect(jsonPath("$[1].startTime").value(at(3)));
    }

    @Test
    void bookedAndCancelledSlotsDoNotTakeUpPageSpace() throws Exception {
        for (int i = 0; i < 10; i++) {
            slot(courtId, i, i % 2 == 0 ? "BOOKED" : "CANCELLED");
        }
        openSlots(courtId, 11, 10);
        mvc.perform(get("/slots").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // --- response shape ---

    @Test
    void responseIncludesSlotIdProviderServiceTimesAndPrice() throws Exception {
        slot(courtId, 0, "OPEN");
        mvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotId").isNumber())
                .andExpect(jsonPath("$[0].providerName").value("Court 3"))
                .andExpect(jsonPath("$[0].serviceName").value("Singles Court Rental"))
                .andExpect(jsonPath("$[0].startTime").value(at(0)))
                .andExpect(jsonPath("$[0].endTime").value(at(1)))
                .andExpect(jsonPath("$[0].price").value(20.0));
    }

    // --- validation ---

    @Test
    void pageZero_returns400() throws Exception {
        openSlots(courtId, 3, 0);
        mvc.perform(get("/slots").param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void negativePage_returns400() throws Exception {
        mvc.perform(get("/slots").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericPage_returns400() throws Exception {
        mvc.perform(get("/slots").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSessionType_returns400() throws Exception {
        mvc.perform(get("/slots").param("sessionType", "FOO"))
                .andExpect(status().isBadRequest());
    }

    // --- M2 filters and time window ---

    @Test
    void pastOpenSlotsAreNotListed() throws Exception {
        LocalDateTime past = LocalDateTime.now().minusDays(1).withNano(0);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)"
                + " VALUES (?, 1, ?, ?, 'OPEN')", courtId, past, past.plusHours(1));
        slot(courtId, 0, "OPEN");
        mvc.perform(get("/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startTime").value(at(0)));
    }

    @Test
    void serviceIdFilter_returnsOnlyThatServicesSlots() throws Exception {
        long doubles = jdbc.queryForObject("SELECT service_id FROM services WHERE name = ?", Long.class,
                "Doubles Court Rental");
        openSlots(courtId, 3, 0);
        LocalDateTime start = BASE.plusHours(5);
        jdbc.update("INSERT INTO availability_slots (provider_id, service_id, start_time, end_time, status)"
                + " VALUES (?, ?, ?, ?, 'OPEN')", courtId, doubles, start, start.plusHours(1));
        mvc.perform(get("/slots").param("serviceId", String.valueOf(doubles)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceName").value("Doubles Court Rental"));
    }

    @Test
    void dateFilter_returnsOnlySlotsStartingThatDay() throws Exception {
        openSlots(courtId, 30, 0); // BASE 08:00 through the next day 13:00
        mvc.perform(get("/slots").param("date", BASE.toLocalDate().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].startTime").value(at(16)));
        mvc.perform(get("/slots").param("date", BASE.toLocalDate().plusDays(1).toString()).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void malformedOrPastDateAndBadIds_return400() throws Exception {
        mvc.perform(get("/slots").param("date", "not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/slots").param("date", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/slots").param("serviceId", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/slots").param("providerId", "-4")).andExpect(status().isBadRequest());
    }
}
