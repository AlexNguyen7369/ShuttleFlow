package com.shuttleflow.service;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.NotificationRequest;
import com.shuttleflow.repository.NotificationRepository;
import com.shuttleflow.repository.NotificationRepository.NotificationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Outbox status transitions in isolation: repository and gateway are mocked. */
class NotificationServiceTest {
    private NotificationRepository repository;
    private NotificationGateway gateway;
    private ApplicationEventPublisher publisher;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        gateway = mock(NotificationGateway.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new NotificationService(repository, gateway, publisher, new MetricsService(repository), 5, 60);
        when(repository.markSent(eq(9L), any())).thenReturn(1);
        when(repository.findPending(9)).thenReturn(Optional.of(new NotificationRecord(
                9, 57, "BOOKED:57", NotificationRequest.BOOKING_CONFIRMED, "alex@shuttleflow.com", "msg", 0)));
    }

    @Test
    void enqueueWritesEventKeyAndPublishesAfterInsert() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 7, 10, 0);
        when(repository.insert(eq(57L), eq("BOOKED:57"), any(), any(), any())).thenReturn(9L);

        service.enqueue(NotificationRequest.BOOKING_CONFIRMED, "alex@shuttleflow.com",
                new AppointmentDto(57, 7, "Court 3", "Singles", start, start.plusHours(1), "BOOKED"));

        verify(repository).insert(eq(57L), eq("BOOKED:57"), eq(NotificationRequest.BOOKING_CONFIRMED),
                eq("alex@shuttleflow.com"), contains("Singles at Court 3 on Sat Nov 7, 10:00 AM is booked."));
        verify(publisher).publishEvent(new NotificationQueuedEvent(9));
    }

    @Test
    void cancelUsesItsOwnKeySoItIsNotDedupedAgainstTheBooking() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 7, 10, 0);
        service.enqueue(NotificationRequest.BOOKING_CANCELLED, "alex@shuttleflow.com",
                new AppointmentDto(57, 7, "Court 3", "Singles", start, start.plusHours(1), "CANCELLED"));
        verify(repository).insert(eq(57L), eq("CANCELLED:57"), any(), any(), contains("was cancelled."));
    }

    @Test
    void sentMarksSent() {
        when(gateway.send(any())).thenReturn(SendResult.SENT);
        service.dispatch(9);
        verify(repository).markSent(eq(9L), any());
    }

    @Test
    void clientErrorGoesToDeadLetter() {
        when(gateway.send(any())).thenReturn(SendResult.PERMANENT);
        service.dispatch(9);
        verify(repository).markFailed(eq(9L), any());
    }

    @Test
    void retryableFailureRecordsARoundAndStaysForThePoller() {
        when(gateway.send(any())).thenReturn(SendResult.RETRYABLE);
        service.dispatch(9);
        verify(repository).recordFailedRound(eq(9L), any(), eq(5));
        verify(repository, never()).markFailed(anyLong(), any());
    }

    @Test
    void pollerDispatchesEveryDueRow() {
        when(repository.findDue(any(), eq(50))).thenReturn(List.of(9L));
        when(gateway.send(any())).thenReturn(SendResult.SENT);
        service.sendDue();
        verify(repository).markSent(eq(9L), any());
    }

    @Test
    void alreadyFinishedRowIsNotResent() {
        service.dispatch(10);
        verify(gateway, never()).send(any());
    }
}
