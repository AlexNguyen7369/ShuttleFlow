package com.shuttleflow.service;

import com.shuttleflow.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client side of the distribution boundary: one coarse-grained call per business event.
 *
 * Fault handling: connect/read timeouts stop a hung service from hanging the sender; a bounded
 * retry with doubling backoff absorbs brief blips (timeout, refused connection, 5xx). A 4xx is
 * never retried because the same request fails the same way every time. Never throws; the
 * caller turns the result into an outbox status, and the same idempotency key on every attempt
 * makes a resend harmless when a timeout hid a success.
 */
@Service
public class NotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(NotificationGateway.class);

    private final RestClient restClient;
    private final int maxAttempts;
    private final long backoffMs;

    public NotificationGateway(@Value("${shuttleflow.notification.base-url}") String baseUrl,
                               @Value("${shuttleflow.notification.connect-timeout-ms}") int connectTimeoutMs,
                               @Value("${shuttleflow.notification.read-timeout-ms}") int readTimeoutMs,
                               @Value("${shuttleflow.notification.max-attempts}") int maxAttempts,
                               @Value("${shuttleflow.notification.backoff-ms}") long backoffMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public SendResult send(NotificationRequest request) {
        String key = request.getIdempotencyKey();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restClient.post().uri("/mock-notifications").body(request).retrieve().toBodilessEntity();
                log.info("Notification sent key={} attempt={}", key, attempt);
                return SendResult.SENT;
            } catch (HttpClientErrorException e) {
                log.error("Notification rejected key={} status={}", key, e.getStatusCode().value());
                return SendResult.PERMANENT;
            } catch (RestClientException e) {
                log.warn("Notification attempt {}/{} failed key={}: {}", attempt, maxAttempts, key, e.getMessage());
            }
            if (attempt < maxAttempts && !pause(backoffMs << (attempt - 1))) {
                break;
            }
        }
        log.error("Notification gave up after {} attempts key={}", maxAttempts, key);
        return SendResult.RETRYABLE;
    }

    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
