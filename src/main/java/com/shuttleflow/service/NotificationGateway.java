package com.shuttleflow.service;

import com.shuttleflow.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client side of the distribution boundary: one coarse-grained call per business event.
 * Never throws; the caller turns the result into an outbox status.
 */
@Service
public class NotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(NotificationGateway.class);

    private final RestClient restClient;

    public NotificationGateway(@Value("${shuttleflow.notification.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public SendResult send(NotificationRequest request) {
        String key = request.getIdempotencyKey();
        try {
            restClient.post().uri("/mock-notifications").body(request).retrieve().toBodilessEntity();
            log.info("Notification sent key={}", key);
            return SendResult.SENT;
        } catch (HttpClientErrorException e) {
            log.error("Notification rejected key={} status={}", key, e.getStatusCode().value());
            return SendResult.PERMANENT;
        } catch (RestClientException e) {
            log.warn("Notification failed key={}: {}", key, e.getMessage());
            return SendResult.RETRYABLE;
        }
    }
}
