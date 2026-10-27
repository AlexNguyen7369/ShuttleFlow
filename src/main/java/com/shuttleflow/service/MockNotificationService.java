package com.shuttleflow.service;

import com.shuttleflow.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for the remote notification service. Stores each idempotency key once, so a resend
 * of the same event is acknowledged but never delivered twice.
 */
@Service
public class MockNotificationService {
    private static final Logger log = LoggerFactory.getLogger(MockNotificationService.class);

    // shortcut: in-memory, lost on restart; a real receiver would persist its seen keys.
    private final Map<String, NotificationRequest> delivered = new ConcurrentHashMap<>();

    /** Returns true when the notification is new, false when its key was already delivered. */
    public boolean receive(NotificationRequest request) {
        if (request == null || isBlank(request.getRecipient()) || isBlank(request.getType())
                || isBlank(request.getMessage()) || isBlank(request.getIdempotencyKey())) {
            throw new InvalidRequestException("recipient, type, message and idempotencyKey are required.");
        }
        boolean isNew = delivered.putIfAbsent(request.getIdempotencyKey(), request) == null;
        log.info("Mock notification received key={} duplicate={}", request.getIdempotencyKey(), !isNew);
        return isNew;
    }

    public int deliveredCount() {
        return delivered.size();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
