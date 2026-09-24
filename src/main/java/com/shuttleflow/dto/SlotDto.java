package com.shuttleflow.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SlotDto {

    private final Long slotId;
    private final String providerName;
    private final String serviceName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final BigDecimal price;

    public SlotDto(Long slotId, String providerName, String serviceName,
                    LocalDateTime startTime, LocalDateTime endTime, BigDecimal price) {
        this.slotId = slotId;
        this.providerName = providerName;
        this.serviceName = serviceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.price = price;
    }

    public Long getSlotId() {
        return slotId;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
