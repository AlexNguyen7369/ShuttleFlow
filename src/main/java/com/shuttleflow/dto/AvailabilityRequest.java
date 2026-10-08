package com.shuttleflow.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class AvailabilityRequest {
    private final Long serviceId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;

    @JsonCreator
    public AvailabilityRequest(@JsonProperty("serviceId") Long serviceId,
                                @JsonProperty("startTime") LocalDateTime startTime,
                                @JsonProperty("endTime") LocalDateTime endTime) {
        this.serviceId = serviceId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getServiceId() { return serviceId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
}
