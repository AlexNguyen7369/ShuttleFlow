package com.shuttleflow.dto;

import java.math.BigDecimal;

/** A provider-owned service offered in the availability form. */
public final class ServiceDto {
    private final long serviceId;
    private final String name;
    private final int durationMin;
    private final int maxPlayers;
    private final BigDecimal price;

    public ServiceDto(long serviceId, String name, int durationMin, int maxPlayers, BigDecimal price) {
        this.serviceId = serviceId;
        this.name = name;
        this.durationMin = durationMin;
        this.maxPlayers = maxPlayers;
        this.price = price;
    }

    public long getServiceId() { return serviceId; }
    public String getName() { return name; }
    public int getDurationMin() { return durationMin; }
    public int getMaxPlayers() { return maxPlayers; }
    public BigDecimal getPrice() { return price; }
}
