package com.shuttleflow.service;

/** Published inside the booking transaction; handled only after it commits. */
public record NotificationQueuedEvent(long notificationId) { }
