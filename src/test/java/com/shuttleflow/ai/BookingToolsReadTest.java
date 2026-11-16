package com.shuttleflow.ai;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.SlotService;
import com.shuttleflow.service.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Read tools in isolation: the services are mocked, so no database or model is involved. */
class BookingToolsReadTest {
    private final LocalDate today = LocalDate.now();
    private SlotService slotService;
    private AppointmentService appointmentService;
    private MockHttpSession session;
    private BookingTools tools;

    @BeforeEach
    void setUp() {
        slotService = mock(SlotService.class);
        appointmentService = mock(AppointmentService.class);
        session = new MockHttpSession();
        tools = new BookingTools(slotService, appointmentService, session);
    }

    @Test
    void openSlotsAreMergedAcrossDaysSortedAndCappedAtTen() {
        when(slotService.getAvailableSlots(isNull(), isNull(), isNull(), any(), eq(1))).thenReturn(List.of());
        // Day 2 has an earlier-listed id but later times than day 1, to prove the merge sorts by time.
        when(slotService.getAvailableSlots(isNull(), isNull(), isNull(), eq(today.plusDays(2)), eq(1)))
                .thenReturn(slots(100, today.plusDays(2), 6));
        when(slotService.getAvailableSlots(isNull(), isNull(), isNull(), eq(today.plusDays(1)), eq(1)))
                .thenReturn(slots(200, today.plusDays(1), 6));

        String result = tools.findOpenSlots(today.toString(), today.plusDays(3).toString(), null);

        List<String> lines = result.lines().toList();
        assertThat(lines).hasSize(BookingTools.MAX_RESULTS);
        assertThat(lines.get(0)).startsWith("slotId=200 |");
        assertThat(lines.get(6)).startsWith("slotId=100 |");
        verify(slotService, times(4)).getAvailableSlots(isNull(), isNull(), isNull(), any(), eq(1));
    }

    @Test
    void sessionTypeIsPassedToTheServiceAndBadInputComesBackAsText() {
        when(slotService.getAvailableSlots(any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        assertThat(tools.findOpenSlots(null, null, "coaching")).startsWith("No open slots between " + today);
        verify(slotService).getAvailableSlots(isNull(), eq("COACHING"), isNull(), eq(today), eq(1));

        assertThat(tools.findOpenSlots("next friday", null, null)).isEqualTo("Error: Dates must use the format YYYY-MM-DD.");
        assertThat(tools.findOpenSlots(today.toString(), today.plusDays(30).toString(), null))
                .contains("at most 14 days");
    }

    @Test
    void myAppointmentsUseTheSessionUserAndAreCappedAtTen() {
        List<AppointmentDto> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDateTime start = today.plusDays(1).atTime(8, 0).plusHours(i);
            many.add(new AppointmentDto(i + 1, i + 1, "Court 3", "Singles Court Rental", start, start.plusHours(1), "BOOKED"));
        }
        when(appointmentService.list("upcoming", session)).thenReturn(many);

        assertThat(tools.listMyAppointments().lines()).hasSize(BookingTools.MAX_RESULTS)
                .first().asString().startsWith("appointmentId=1 | Court 3");
    }

    @Test
    void loggedOutCallerGetsTheServiceErrorAsText() {
        when(appointmentService.list("upcoming", session)).thenThrow(new UnauthorizedException("Login required."));

        assertThat(tools.listMyAppointments()).isEqualTo("Error: Login required.");
        verify(slotService, never()).getAvailableSlots(any(), any(), any(), any(), anyInt());
    }

    private static List<SlotDto> slots(long firstId, LocalDate day, int count) {
        List<SlotDto> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDateTime start = day.atTime(8 + i, 0);
            out.add(new SlotDto(firstId + i, "Court 3", "Singles Court Rental", start, start.plusHours(1),
                    new BigDecimal("20.00")));
        }
        return out;
    }
}
