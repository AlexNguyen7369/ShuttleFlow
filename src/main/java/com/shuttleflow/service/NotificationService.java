package com.shuttleflow.service;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.NotificationRequest;
import com.shuttleflow.repository.NotificationRepository;
import com.shuttleflow.repository.NotificationRepository.NotificationRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Transactional outbox for booking notifications.
 *
 * enqueue() runs inside the booking/cancel transaction: the outbox row commits with the booking or
 * rolls back with it, so a notification can never announce a rolled-back booking and a crash right
 * after commit cannot lose one. The send happens only after commit, on another thread, so the HTTP
 * call never holds the slot lock or delays the booking response.
 */
@Service
public class NotificationService {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US);

    private final NotificationRepository notificationRepository;
    private final NotificationGateway notificationGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxRounds;
    private final long staleAfterSeconds;

    public NotificationService(NotificationRepository notificationRepository, NotificationGateway notificationGateway,
                               ApplicationEventPublisher eventPublisher,
                               @Value("${shuttleflow.notification.max-rounds}") int maxRounds,
                               @Value("${shuttleflow.notification.stale-after-seconds}") long staleAfterSeconds) {
        this.notificationRepository = notificationRepository;
        this.notificationGateway = notificationGateway;
        this.eventPublisher = eventPublisher;
        this.maxRounds = maxRounds;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    /** Must be called inside the transaction that books or cancels the appointment. */
    public void enqueue(String type, String recipient, AppointmentDto appointment) {
        boolean confirmed = NotificationRequest.BOOKING_CONFIRMED.equals(type);
        // Built from the event, not the payload: a cancel-and-rebook has identical details but a new appointment id.
        String key = (confirmed ? "BOOKED:" : "CANCELLED:") + appointment.getAppointmentId();
        String message = "Your " + appointment.getServiceName() + " at " + appointment.getProviderName() + " on "
                + appointment.getStartTime().format(WHEN) + (confirmed ? " is booked." : " was cancelled.");
        long notificationId = notificationRepository.insert(appointment.getAppointmentId(), key, type, recipient, message);
        eventPublisher.publishEvent(new NotificationQueuedEvent(notificationId));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(NotificationQueuedEvent event) {
        dispatch(event.notificationId());
    }

    /**
     * Backstop for the after-commit send: runs on a timer, so it needs no crash detection. Only rows
     * older than stale-after-seconds are picked up, so it does not race a fast-path send in flight.
     */
    @Scheduled(fixedDelayString = "${shuttleflow.notification.poll-ms}",
            initialDelayString = "${shuttleflow.notification.poll-ms}")
    public void sendDue() {
        for (long notificationId : notificationRepository.findDue(LocalDateTime.now().minusSeconds(staleAfterSeconds), 50)) {
            dispatch(notificationId);
        }
    }

    /** One delivery round for a PENDING row; a row another sender already finished is skipped. */
    public void dispatch(long notificationId) {
        NotificationRecord row = notificationRepository.findPending(notificationId).orElse(null);
        if (row == null) {
            return;
        }
        NotificationRequest request = new NotificationRequest(row.recipient(), row.type(), row.message(),
                row.idempotencyKey(), row.appointmentId());
        LocalDateTime now = LocalDateTime.now();
        switch (notificationGateway.send(request)) {
            case SENT -> notificationRepository.markSent(notificationId, now);
            case PERMANENT -> notificationRepository.markFailed(notificationId, now);
            case RETRYABLE -> notificationRepository.recordFailedRound(notificationId, now, maxRounds);
        }
    }
}
