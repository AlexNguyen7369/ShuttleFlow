package com.shuttleflow.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class BookingRequest {
    private final Long slotId;
    private final Long serviceId;

    @JsonCreator
    public BookingRequest(@JsonProperty("slotId") Long slotId, @JsonProperty("serviceId") Long serviceId) {
        this.slotId = slotId;
        this.serviceId = serviceId;
    }

    public Long getSlotId() { return slotId; }
    public Long getServiceId() { return serviceId; }
}
