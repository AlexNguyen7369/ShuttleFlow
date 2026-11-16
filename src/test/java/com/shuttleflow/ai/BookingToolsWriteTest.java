package com.shuttleflow.ai;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.BookingConflictException;
import com.shuttleflow.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Confirm-before-write in code: services are mocked, so only the propose/confirm guard is tested. */
class BookingToolsWriteTest {
    private SlotService slotService;
    private AppointmentService appointmentService;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        slotService = mock(SlotService.class);
        appointmentService = mock(AppointmentService.class);
        session = new MockHttpSession();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        when(appointmentService.book(any(), any())).thenReturn(
                new AppointmentDto(50, 7, "Court 3", "Singles Court Rental", start, start.plusHours(1), "BOOKED"));
    }

    private BookingTools turn(int number) {
        return new BookingTools(slotService, appointmentService, session, number);
    }

    @Test
    void confirmInTheSameTurnAsTheProposalIsRefused() {
        BookingTools tools = turn(1);
        tools.proposeBooking(7);

        assertThat(tools.confirmBooking()).startsWith("Error: no booking is waiting");
        verify(appointmentService, never()).book(any(), any());
    }

    @Test
    void confirmInALaterTurnBooksThroughTheServiceOnce() {
        turn(1).proposeBooking(7);

        assertThat(turn(2).confirmBooking()).startsWith("Booked: appointmentId=50 | Court 3");
        ArgumentCaptor<BookingRequest> request = ArgumentCaptor.forClass(BookingRequest.class);
        verify(appointmentService).book(request.capture(), any());
        assertThat(request.getValue().getSlotId()).isEqualTo(7L);

        // The pending action is used up: a second "yes" cannot book again.
        assertThat(turn(3).confirmBooking()).startsWith("Error: no booking is waiting");
    }

    @Test
    void confirmWithoutAnyProposalIsRefused() {
        assertThat(turn(5).confirmBooking()).startsWith("Error: no booking is waiting");
        assertThat(turn(5).confirmCancel()).startsWith("Error: no cancellation is waiting");
    }

    @Test
    void aProposedCancelCannotBeConfirmedAsABooking() {
        turn(1).proposeCancel(50);

        assertThat(turn(2).confirmBooking()).startsWith("Error: no booking is waiting");
        assertThat(turn(2).confirmCancel()).isEqualTo("Cancelled appointmentId=50.");
        verify(appointmentService).cancel(50L, session);
        verify(appointmentService, never()).book(any(), any());
    }

    @Test
    void bookingConflictComesBackAsTheStandardMessage() {
        when(appointmentService.book(any(), any())).thenThrow(new BookingConflictException());
        turn(1).proposeBooking(7);

        assertThat(turn(2).confirmBooking()).isEqualTo("Error: Court is already booked.");
    }

    @Test
    void invalidIdsAreRejectedWithoutStoringAnything() {
        assertThat(turn(1).proposeBooking(0)).startsWith("Error:");
        assertThat(turn(1).proposeCancel(-1)).startsWith("Error:");
        assertThat(session.getAttribute(PendingAction.SESSION_KEY)).isNull();
        verify(appointmentService, never()).cancel(anyLong(), any());
    }
}
