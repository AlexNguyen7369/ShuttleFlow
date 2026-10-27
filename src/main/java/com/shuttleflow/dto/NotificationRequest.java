package com.shuttleflow.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The whole notification for one business event, sent in a single call across the service
 * boundary. Self-contained, so the receiver never needs to read ShuttleFlow's database.
 */
public final class NotificationRequest {
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";

    private final String recipient;
    private final String type;
    private final String message;
    private final String idempotencyKey;
    private final Long appointmentId;

    @JsonCreator
    public NotificationRequest(@JsonProperty("recipient") String recipient, @JsonProperty("type") String type,
                               @JsonProperty("message") String message,
                               @JsonProperty("idempotencyKey") String idempotencyKey,
                               @JsonProperty("appointmentId") Long appointmentId) {
        this.recipient = recipient;
        this.type = type;
        this.message = message;
        this.idempotencyKey = idempotencyKey;
        this.appointmentId = appointmentId;
    }

    public String getRecipient() { return recipient; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getAppointmentId() { return appointmentId; }
}
