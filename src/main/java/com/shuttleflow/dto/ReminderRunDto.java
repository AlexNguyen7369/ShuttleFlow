package com.shuttleflow.dto;

/** Outcome of one reminder agent run. */
public final class ReminderRunDto {
    private final int scanned;
    private final int queued;
    private final int fallbacks;

    public ReminderRunDto(int scanned, int queued, int fallbacks) {
        this.scanned = scanned;
        this.queued = queued;
        this.fallbacks = fallbacks;
    }

    public int getScanned() { return scanned; }
    public int getQueued() { return queued; }
    public int getFallbacks() { return fallbacks; }
}
