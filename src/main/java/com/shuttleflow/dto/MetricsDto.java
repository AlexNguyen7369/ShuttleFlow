package com.shuttleflow.dto;

public final class MetricsDto {
    private final long bookingsCreated;
    private final long bookingConflicts;
    private final long notificationsSent;
    private final long notificationFailures;
    private final long pendingNotifications;
    private final long remindersQueued;
    private final long reminderFallbacks;

    public MetricsDto(long bookingsCreated, long bookingConflicts, long notificationsSent, long notificationFailures,
                      long pendingNotifications, long remindersQueued, long reminderFallbacks) {
        this.bookingsCreated = bookingsCreated;
        this.bookingConflicts = bookingConflicts;
        this.notificationsSent = notificationsSent;
        this.notificationFailures = notificationFailures;
        this.pendingNotifications = pendingNotifications;
        this.remindersQueued = remindersQueued;
        this.reminderFallbacks = reminderFallbacks;
    }

    public long getBookingsCreated() { return bookingsCreated; }
    public long getBookingConflicts() { return bookingConflicts; }
    public long getNotificationsSent() { return notificationsSent; }
    public long getNotificationFailures() { return notificationFailures; }
    public long getPendingNotifications() { return pendingNotifications; }
    public long getRemindersQueued() { return remindersQueued; }
    public long getReminderFallbacks() { return reminderFallbacks; }
}
