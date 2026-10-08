package com.shuttleflow.dto;

import java.time.LocalDateTime;

public final class ProviderAppointmentDto {
    private final long appointmentId;
    private final long slotId;
    private final String serviceName;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String customerName;
    private final String customerEmail;

    public ProviderAppointmentDto(long appointmentId, long slotId, String serviceName,
                                  LocalDateTime startTime, LocalDateTime endTime,
                                  String customerName, String customerEmail) {
        this.appointmentId = appointmentId;
        this.slotId = slotId;
        this.serviceName = serviceName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
    }

    public long getAppointmentId() { return appointmentId; }
    public long getSlotId() { return slotId; }
    public String getServiceName() { return serviceName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
}
