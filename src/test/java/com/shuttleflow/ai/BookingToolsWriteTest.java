package com.shuttleflow.ai;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.service.AiConversationService;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.BookingConflictException;
import com.shuttleflow.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Confirm-before-write in code: the booking services are mocked, and the pending action lives in the
 * real ai_conversations table, so only the propose/confirm guard is tested.
 */
@SpringBootTest
class BookingToolsWriteTest {
    private static final long ALEX = 3L;
    @MockitoBean private SlotService slotService;
    @MockitoBean private AppointmentService appointmentService;
    @Autowired private AiConversationService conversations;
    @Autowired private JdbcTemplate jdbc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM ai_conversations");
        session = new MockHttpSession();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        when(appointmentService.book(any(), any())).thenReturn(
                new AppointmentDto(50, 7, "Court 3", "Singles Court Rental", start, start.plusHours(1), "BOOKED"));
    }

    private BookingTools turn(int number) {
        return new BookingTools(slotService, appointmentService, conversations, session, ALEX, number);
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
    void aProposalExpiresOnceTheUserMovesOn() {
        turn(1).proposeBooking(7);

        assertThat(turn(3).confirmBooking()).startsWith("Error: no booking is waiting");
        assertThat(turn(4).confirmBooking()).startsWith("Error: no booking is waiting");
        verify(appointmentService, never()).book(any(), any());
    }

    @Test
    void aNewLoginClearsAPendingProposal() {
        turn(1).proposeBooking(7);
        conversations.reset(ALEX); // what login does

        assertThat(turn(2).confirmBooking()).startsWith("Error: no booking is waiting");
        verify(appointmentService, never()).book(any(), any());
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations WHERE pending_kind IS NOT NULL",
                Integer.class)).isZero();
        verify(appointmentService, never()).cancel(anyLong(), any());
    }
}
