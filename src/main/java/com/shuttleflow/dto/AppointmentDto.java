package com.shuttleflow.dto;

import java.time.LocalDateTime;

public final class AppointmentDto {
    private final long appointmentId;
    private final long slotId;
    private final String providerName;
    private final String serviceName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String status;

    public AppointmentDto(long appointmentId, long slotId, String providerName, String serviceName,
                          LocalDateTime startTime, LocalDateTime endTime, String status) {
        this.appointmentId = appointmentId;
        this.slotId = slotId;
        this.providerName = providerName;
        this.serviceName = serviceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public long getAppointmentId() { return appointmentId; }
    public long getSlotId() { return slotId; }
    public String getProviderName() { return providerName; }
    public String getServiceName() { return serviceName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getStatus() { return status; }
}
