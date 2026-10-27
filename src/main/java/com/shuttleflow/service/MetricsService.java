package com.shuttleflow.service;

import com.shuttleflow.dto.MetricsDto;
import com.shuttleflow.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Running totals since startup; a monitoring tool derives rates (e.g. failures per hour) from them.
 * shortcut: in-memory, reset on restart; move to Micrometer if a real monitoring stack is added.
 */
@Service
public class MetricsService {
    private final AtomicLong bookingsCreated = new AtomicLong();
    private final AtomicLong bookingConflicts = new AtomicLong();
    private final AtomicLong notificationsSent = new AtomicLong();
    private final AtomicLong notificationFailures = new AtomicLong();
    private final NotificationRepository notificationRepository;

    public MetricsService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void bookingCreated() { bookingsCreated.incrementAndGet(); }
    public void bookingConflict() { bookingConflicts.incrementAndGet(); }
    public void notificationSent() { notificationsSent.incrementAndGet(); }
    public void notificationFailed() { notificationFailures.incrementAndGet(); }

    public MetricsDto snapshot() {
        return new MetricsDto(bookingsCreated.get(), bookingConflicts.get(), notificationsSent.get(),
                notificationFailures.get(), notificationRepository.countPending());
    }
}
